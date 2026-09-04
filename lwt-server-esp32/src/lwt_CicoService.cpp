#include "lwt_CicoService.h"
#include "esp_timer.h"
#include "esp_sntp.h"
#include "operations_generated.h"
#include "cico_generated.h"
#include "lwt_ApplicationServer.h"
#include "lwt_SecureTokenFormat.h"
#include "BitConverter.h"
#include "SystemTime.h"
#include "flatbuffer_util.h"
#include "esp_log.h"
#include "Base32.h"
#include "MessageDigest.h"
#include "flatbuffer_lwttime.h"
#include "lwdn_WifiNanLink.h"
#include "lwdn_BleLink.h"
#include "lwt_CertRole.h"

namespace lwt {

    static constexpr const char* TAG = "CicoService";

    using BC = BitConverter<std::endian::big>;

    static constexpr uint16_t CONFIRMATION_TOKEN_VERSION = 1;
    static constexpr uint16_t REFRESH_TOKEN_VERSION = 1;
    static constexpr size_t SEED_DERIVATION_SECRET_SIZE = 32;

    CicoService::CicoService(
        const TicketValidationConfig& config,
        Certificate& trustRoot, Certificate& deviceCert, DigitalSignature& signingKey, HMACSHA256& hmac,
        TicketValidationService& ticketValidationService, MOSClient& mosClient,
        int syncTaskPriority
    ) :
        m_Config(config),
        m_TrustRoot(trustRoot),
        m_DeviceCert(deviceCert),
        m_SigningKey(signingKey),
        m_HMAC(hmac),
        m_TicketValidationService(ticketValidationService),
        m_MOSClient(mosClient)
    {
        xTaskCreateStaticPSRAM(SyncEventsTaskFunc, "CicoSync", 4096, this, syncTaskPriority, &m_SyncTask);
        m_SeedDerivationSecret.resize(SEED_DERIVATION_SECRET_SIZE);
    }

    CicoService::~CicoService() {
        std::unique_lock lock(m_EventsMutex);
        m_RequestClose = true;
        m_HasEventsCV.notify_all();
        m_Closed.wait(lock, [this] { return !m_SyncTask; });
    }

    bool CicoService::IsCicoReady() {
        sntp_sync_status_t status = esp_sntp_get_sync_status();
        if (status == SNTP_SYNC_STATUS_COMPLETED) {
            // esp_sntp_get_sync_status resets the status to SNTP_SYNC_STATUS_RESET after returning SNTP_SYNC_STATUS_COMPLETED,
            // we want it to stay
            esp_sntp_set_sync_status(SNTP_SYNC_STATUS_COMPLETED);
        }
        return status != SNTP_SYNC_STATUS_RESET;
    }

    void CicoService::SyncEventsLoop() {
        std::unique_lock lock(m_EventsMutex);
        while (!m_RequestClose) {
            m_HasEventsCV.wait(lock, [this] { return !m_EventBuffer.empty() || m_RequestClose; });
            SendEventsToServer();
            m_EventBuffer = {};
            if (m_RequestClose) {
                break;
            }
        }
        m_Closed.notify_all();
        vTaskDelete(nullptr);
    }

    bool CicoService::SendEventsToServer() {
        if (!m_EventBuffer.empty()) {
            MOSCICOEventBatch eventBatch{
                .Events = std::move(m_EventBuffer),
                .CurrentLocalTimestamp = SystemTime::UptimeMillis()
            };
            int statusCode = m_MOSClient.CICOPushEvents(eventBatch);
            if (statusCode != 200) {
                // move data back, retry later
                m_EventBuffer = std::move(eventBatch.Events);
                return false;
            }
        }
        return true;
    }

    void CicoService::SyncEventsTaskFunc(void* arg) {
        CicoService* service = static_cast<CicoService*>(arg);
        service->SyncEventsLoop();
    }

    void CicoService::Register(ServiceRegistry& registry) {
        registry.RegisterServiceCallback(
            Operation_CICOCheckIn,
            ApplicationServer::CreateOperationServiceFunc<CheckInRequest>(
                [this](const CheckInRequest& request, flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                    if (!IsCicoReady()) {
                        return 503; // service unavailable
                    }

                    auto* checkinToken = request.account_checkin_token();
                    MOSCheckInRequest req;
                    req.CheckInToken.assign(checkinToken->begin(), checkinToken->end());

                    MOSCheckInResponse resp;
                    int statusCode = m_MOSClient.CICOCheckIn(req, &resp);
                    if (!MOSClient::IsStatusOK(statusCode)) {
                        return statusCode;
                    }

                    auto confirmationToken = CreateConfirmationToken(resp);

                    fbb.Finish(CreateCheckInIntermediate(
                        fbb,
                        fbb.CreateVector(confirmationToken)
                    ));

                    return 200;
                }
            )
        );
        registry.RegisterServiceCallback(
            Operation_CICOCheckInConfirm,
            ApplicationServer::CreateOperationServiceFunc<CheckInConfirmation>(
                [this](const CheckInConfirmation& request, flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                    if (!IsCicoReady()) {
                        return 503; // service unavailable
                    }
                    if (m_TicketValidationService.IsRazzia()) {
                        return 409; // conflict - can not check in during razzia
                    }

                    auto confirmationToken = flatbuffers::make_span(*request.confirmation_token());
                    auto time = SystemTime::EpochMillis();

                    if (!VerifyConfirmationToken(confirmationToken)) {
                        return 403;
                    }

                    MOSCheckInResponse checkIn;
                    int64_t timestamp;
                    if (!ParseConfirmationToken(confirmationToken, &checkIn, &timestamp)) {
                        return 400;
                    }

                    if (time - timestamp > m_Config.CicoConfirmationTokenExpiryMs) {
                        return 410;
                    }

                    psram_string metadata = m_TicketValidationService.GetCurrentValidationMetadata();

                    ProcessCicoRequest(EventStartCico(checkIn, metadata), fbb);

                    return 200;
                }
            )
        );
        registry.RegisterServiceCallback(
            Operation_CICORefresh,
            ApplicationServer::CreateOperationServiceFunc<CICORefreshRequest>(
                [this](const CICORefreshRequest& request, flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                    if (!IsCicoReady()) {
                        return 503; // service unavailable
                    }

                    auto time = SystemTime::EpochMillis();

                    ParsedRefreshToken parsedToken;
                    bool sameIssuer;
                    int status = VerifyAndParseRefreshToken(*request.fragment(), &parsedToken, &sameIssuer);
                    if (status) {
                        return status;
                    }

                    bool isRazzia = m_TicketValidationService.IsRazzia();
                    bool canRefresh;
                    if (!isRazzia) {
                        canRefresh = true;
                    }
                    else {
                        // refresh is possible if any of the tokens was issued recently enough
                        canRefresh = time < parsedToken.IssuedAt + m_Config.CicoConfirmationTokenExpiryMs;
                    }

                    if (!canRefresh) {
                        ESP_LOGW(TAG, "Cannot refresh ticket during razzia");
                        return 409; // conflict
                    }

                    psram_string metadata = m_TicketValidationService.GetCurrentValidationMetadata();

                    ProcessCicoRequest(EventRefreshCico(parsedToken, metadata, MOSCICOEventType::REFRESH), fbb);

                    return 200;
                }
            )
        );
        registry.RegisterServiceCallback(
            Operation_CICOCheckOut,
            ApplicationServer::CreateOperationServiceFunc<CheckOutRequest>(
                [this](const CheckOutRequest& request, flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                    if (!IsCicoReady()) {
                        return 503; // service unavailable
                    }

                    ParsedRefreshToken parsedToken;
                    int status = VerifyAndParseRefreshToken(*request.fragment(), &parsedToken, nullptr);
                    if (status) {
                        return status;
                    }

                    fbb.Finish(CreateCheckOutResponse(fbb, CreateVector(fbb, ByteSpan(parsedToken.SessionId))));

                    EnqueuePushEvent(EventRefreshCico(parsedToken, m_TicketValidationService.GetCurrentValidationMetadata(), MOSCICOEventType::CHECK_OUT));

                    return 200;
                }
            )
        );
    }

    int CicoService::VerifyAndParseRefreshToken(const CICOFragmentRefreshRequest& request, ParsedRefreshToken* pParsedToken, bool* pSameIssuer) {
        ByteSpan refreshToken = flatbuffers::make_span(*request.refresh_token());
        bool sameIssuer = IsRefreshSelfCertificate(request);
        if (pSameIssuer) {
            *pSameIssuer = sameIssuer;
        }
        bool authentic;
        if (sameIssuer) {
            authentic = SecureToken::VerifySignedToken(m_DeviceCert, refreshToken);
        }
        else {
            try {
                Certificate issuerCert(flatbuffers::make_span(*request.issuer_certificate()));
                if (!IsIssuerTrusted(issuerCert)) {
                    ESP_LOGW(TAG, "Issuer certificate is not trusted");
                    return 403;
                }
                authentic = SecureToken::VerifySignedToken(issuerCert, refreshToken);
            }
            catch (const std::invalid_argument& e) {
                ESP_LOGE(TAG, "Could not load certificate: %s", e.what());
                return 400;
            }
        }
        if (!authentic) {
            ESP_LOGW(TAG, "Refresh token is inauthentic");
            return 403;
        }
        if (!ParseRefreshToken(refreshToken, pParsedToken)) {
            ESP_LOGW(TAG, "Could not parse refresh token");
            return 400;
        }
        return 0;
    }

    void CicoService::ProcessCicoRequest(MOSCICOEvent&& event, flatbuffers::FlatBufferBuilder& responseFbb) {
        int64_t epochMs = SystemTime::EpochMillis();

        int64_t ticketValidFrom = epochMs;
        int64_t ticketValidUntil = epochMs + m_Config.CicoTicketTtlMs;

        auto etd = GenerateETD(ticketValidFrom, ticketValidUntil, event.SessionId, event.LwtMetadata);

        ByteVector signature;
        SignETD(etd, &signature);

        auto totpSeed = DeriveTotpSeed(signature);

        LwtOffsetDateTime issuedAtAbsolute = IsoToFlatLwt(OffsetDateTime::of_local_epoch_seconds(epochMs / 1000));

        responseFbb.Finish(CreateCICOTicketFragment(
            responseFbb,
            GetDeviceAddressesToFlatbuffer(responseFbb),
            CreateVector(responseFbb, m_DeviceCert.GetCertificateDer()),
            responseFbb.CreateVector(etd),
            CreateVector(responseFbb, ByteSpan(totpSeed)),
            responseFbb.CreateVector(CreateRefreshToken(epochMs, event)),
            ticketValidFrom,
            &issuedAtAbsolute,
            m_Config.CicoTicketTtlMs
        ));

        EnqueuePushEvent(std::move(event));
    }

    ByteVector CicoService::CreateConfirmationToken(const MOSCheckInResponse& checkIn) {
        ByteVector token(SecureToken::CalcHmacTokenSize(m_HMAC, sizeof(uint16_t) + checkIn.SessionId.size() + sizeof(checkIn.AccountId) + sizeof(int64_t)));

        BC::OutputStream out(token.data());
        out.WriteUInt16(CONFIRMATION_TOKEN_VERSION);
        out.WriteBytes(checkIn.SessionId);
        out.WriteUInt32(checkIn.AccountId);
        out.WriteInt64(SystemTime::EpochMillis());

        SecureToken::AddHmac(m_HMAC, token);
        return token;
    }

    bool CicoService::VerifyConfirmationToken(const ByteSpan& token) {
        return SecureToken::VerifyHmac(m_HMAC, token);
    }

    bool CicoService::ParseConfirmationToken(const ByteSpan& token, MOSCheckInResponse* pCheckIn, int64_t* pTimestamp) {
        BC::InputStream in(token.data());

        uint16_t version = in.ReadUInt16();
        if (version != CONFIRMATION_TOKEN_VERSION) {
            return false;
        }

        UUID sessionId = in.ReadBytesAs<UUID>();
        uint32_t accountId = in.ReadUInt32();
        int64_t timestamp = in.ReadInt64();

        pCheckIn->SessionId = UUID(sessionId);
        pCheckIn->AccountId = accountId;
        *pTimestamp = timestamp;

        return true;
    }

    MOSCICOEvent CicoService::EventStartCico(const MOSCheckInResponse& checkIn, const psram_string& metadata) {
        return MOSCICOEvent{
            .EventId = UUID::V7(),
            .PreviousEventId = UUID::Nil(),
            .SessionId = checkIn.SessionId,
            .AccountId = checkIn.AccountId,
            .LocalTimestamp = SystemTime::UptimeMillis(),
            .AbsoluteTimestamp = OffsetDateTime::now(),
            .EventType = MOSCICOEventType::CHECK_IN,
            .LwtMetadata = metadata
        };
    }

    MOSCICOEvent CicoService::EventRefreshCico(const ParsedRefreshToken& refreshToken, const psram_string& metadata, MOSCICOEventType type) {
        return MOSCICOEvent{
            .EventId = UUID::V7(),
            .PreviousEventId = refreshToken.PreviousEventId,
            .SessionId = refreshToken.SessionId,
            .AccountId = refreshToken.AccountId,
            .LocalTimestamp = SystemTime::UptimeMillis(),
            .AbsoluteTimestamp = OffsetDateTime::now(),
            .EventType = type,
            .LwtMetadata = metadata
        };
    }

    void CicoService::EnqueuePushEvent(MOSCICOEvent&& event) {
        std::unique_lock lock(m_EventsMutex);
        m_EventBuffer.push_back(std::move(event));
        m_HasEventsCV.notify_all();
    }

    ByteVector CicoService::GenerateETD(int64_t validFromEMs, int64_t validToEMs, const UUID& sessionId, const psram_string& metadata) {
        psram_string etdString = "ETD*1*IN:"
            + psram_string(m_Config.TicketIssuerID)
            + "*VS:" + OffsetDateTime::of_local_epoch_seconds(validFromEMs / 1000).to_string<psram_string>()
            + "*VU:" + OffsetDateTime::of_local_epoch_seconds(validToEMs / 1000).to_string<psram_string>()
            + "*X-SID:" + psram_string(sessionId.ToString())
            + "*X-LWT:" + metadata
            + "*";

        return ByteVector(etdString.begin(), etdString.end());
    }

    void CicoService::SignETD(const ByteVector& etd, ByteVector* pSignature) {
        ByteVector sig;
        int res = m_SigningKey.Sign(etd, &sig);
        if (res != 0) {
            ESP_LOGE(TAG, "Failed to sign ETD: %d", res);
            return;
        }
        psram_string suffix = "SG:" + Base32Hex::Encode<psram_string>(sig) + "*";
        sig.insert(sig.end(), suffix.begin(), suffix.end());
        if (pSignature) {
            *pSignature = std::move(sig);
        }
    }

    flatbuffers::Offset<flatbuffers::Vector<uint8_t>> CicoService::GetSeedDerivationSecretToFlatbuffer(flatbuffers::FlatBufferBuilder& builder) {
        std::lock_guard lock(m_SeedDerivationMutex);

        UpdateSeedDerivationSecret();
        return builder.CreateVector(m_SeedDerivationSecret);
    }

    SHA256Hash CicoService::DeriveTotpSeed(const ByteSpan& ticketSignature) {
        std::lock_guard lock(m_SeedDerivationMutex);

        ByteVector mergedSecret;
        mergedSecret.insert(mergedSecret.end(), ticketSignature.begin(), ticketSignature.end());
        mergedSecret.insert(mergedSecret.end(), m_SeedDerivationSecret.begin(), m_SeedDerivationSecret.end());

        return MessageDigest::SHA256(mergedSecret);
    }

    void CicoService::UpdateSeedDerivationSecret() {
        auto tripKey = m_TicketValidationService.GetCurrentTripKey();
        if (tripKey != m_LastSeedTripKey) {
            m_LastSeedTripKey = tripKey;
            psa_generate_random(m_SeedDerivationSecret.data(), m_SeedDerivationSecret.size());
        }
    }

    flatbuffers::Offset<flatbuffers::Vector<flatbuffers::Offset<LwdnAddress>>> CicoService::GetDeviceAddressesToFlatbuffer(flatbuffers::FlatBufferBuilder& builder) {
        std::vector<flatbuffers::Offset<LwdnAddress>> addresses;

        addresses.push_back(CreateLwdnAddress(builder, LwdnLinkType_BluetoothLe, CreateVector(builder, ByteSpan(lwdn::BLE_ADAPTER.GetLinkAddress()))));
        addresses.push_back(CreateLwdnAddress(builder, LwdnLinkType_WifiAware, CreateVector(builder, ByteSpan(lwdn::WIFI_NAN_ADAPTER.GetLinkAddress()))));

        return builder.CreateVector(addresses);
    }

    ByteVector CicoService::CreateRefreshToken(int64_t issuedAt, const MOSCICOEvent& prevEvent) {
        ByteVector token(sizeof(uint16_t) + sizeof(int64_t) + sizeof(uint32_t) + UUID{}.size() + UUID{}.size());

        BC::OutputStream out(token.data());
        out.WriteUInt16(REFRESH_TOKEN_VERSION);
        out.WriteInt64(issuedAt);
        out.WriteUInt32(prevEvent.AccountId);
        out.WriteBytes(prevEvent.SessionId);
        out.WriteBytes(prevEvent.EventId);

        return SecureToken::CreateSignedToken(m_SigningKey, std::move(token));
    }

    bool CicoService::ParseRefreshToken(const ByteSpan& token, ParsedRefreshToken* pParsedToken) {
        BC::InputStream in(token.data());

        uint16_t version = in.ReadUInt16();
        if (version != REFRESH_TOKEN_VERSION) {
            return false;
        }

        int64_t issuedAt = in.ReadInt64();
        uint32_t accountId = in.ReadUInt32();
        UUID sessionId = in.ReadBytesAs<UUID>();
        UUID previousEventId = in.ReadBytesAs<UUID>();

        *pParsedToken = ParsedRefreshToken{
            .IssuedAt = issuedAt,
            .AccountId = accountId,
            .SessionId = sessionId,
            .PreviousEventId = previousEventId
        };

        return true;
    }

    bool CicoService::IsIssuerTrusted(Certificate& issuerCert) {
        int result = m_TrustRoot.VerifyChildCertificate(issuerCert);
        if (result != 0) {
            ESP_LOGW(TAG, "Issuer certificate verification failed: %d", result);
            return false;
        }
        auto heldRoles = CertRoleUtil::ExtractRolesFromCert(issuerCert);
        if (!CertRoleUtil::CheckAnyRole(heldRoles, CertRole::LWT_DEVICE)) {
            ESP_LOGW(TAG, "Rejecting issuer as it does not have the LWT_DEVICE role");
            return false;
        }
        return true;
    }

    bool CicoService::IsRefreshSelfCertificate(const CICOFragmentRefreshRequest& request) {
        return request.issuer_certificate()->size() == 1 && request.issuer_certificate()->Get(0) == 0x5C;
    }
}