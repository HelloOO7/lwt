#include "lwt_TicketValidationService.h"

#include <unordered_set>
#include "ISO8601.h"
#include "operations_generated.h"
#include "ticket_validation_generated.h"
#include "lwt_ApplicationServer.h"
#include "StringExtensions.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "BitConverter.h"
#include "MessageDigest.h"
#include <cassert>
#include "ISO8601.h"
#include "SystemTime.h"
#include "flatbuffer_lwttime.h"
#include "lwt_SecureTokenFormat.h"

namespace lwt {

    static constexpr const char* TAG = "TicketValidationService";

    using namespace vdv301;

    using BC = BitConverter<std::endian::big>;

    TicketValidationService::TicketValidationService(
        const TicketValidationConfig& config,
        PreauthorizationTokenManager& tokenManager, TicketSignatureVerifier& ticketVerifier,
        MOSClient& mosClient,
        TripInformationService& tripInfoService, SubscriberTVS* tvsOpt,
        PublisherRCS* rcsOpt
    ) :
        m_Config{ config },
        m_TokenManager{ tokenManager },
        m_PreauthRateLimiter(1000, 45LL * 60 * 1000), // 1000 entries, 45 minutes max age
        m_TicketVerifier{ ticketVerifier },
        m_MOSClient{ mosClient },
        m_TripInfoService{ tripInfoService },
        m_TVS{ tvsOpt },
        m_RCS{ rcsOpt },
        m_RazziaOffTimer(
            [this]() {
                std::lock_guard lock(m_DataMutex);
                // prevent race conditions - check timestamp again anyway
                ClearLocalRazziaIfExpired();
            },
            RAZZIA_HEARTBEAT_MAX_SECONDS * 1000 * 1000, TimerProc::Type::ONESHOT // 30 seconds
        )
    {
        m_TripInfoService.ObserveTripRouteInfo(*this);
        if (m_TVS) {
            m_TVS->ObserveCurrentTariffStop(*this);
            m_TVS->ObserveRazziaState(*this);
        }
    }

    TicketValidationService::~TicketValidationService() {
        m_TripInfoService.RemoveObserver(*this);
        if (m_TVS) {
            m_TVS->RemoveObserver((Observer<SubscriberTVS::CurrentTariffStop>&) * this);
            m_TVS->RemoveObserver((Observer<SubscriberTVS::RazziaState>&) * this);
        }
    }

    void TicketValidationService::Register(ServiceRegistry& registry)
    {
        registry.RegisterServiceCallback(
            Operation_GetTicketValidationInfo,
            [&](const RequestPacket& request) -> OperationResult {
                std::lock_guard lock(m_DataMutex);
                if (!m_HasData) {
                    return 404;
                }
                return OperationResult(200, SerializeFlatBuffer(m_ValidationInfoFBB));
            }
        );

        registry.RegisterServiceCallback(
            Operation_CreatePreauthorizationToken,
            ApplicationServer::CreateOperationServiceFunc<PreauthorizationTokenRequest>(
                [&](const PreauthorizationTokenRequest& request, flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                    bool isRazzia = IsRazzia();

                    std::lock_guard lock(m_TokenGeneratorMutex);

                    auto currentTime = SystemTime::UptimeMillis();
                    auto newTokenExpiryTime = currentTime + m_Config.PreauthorizationGracePeriodMs;

                    std::vector<SHA256Hash> tokenHashes; // can be a view, as it looks inside the request flatbuffer

                    // validate and create input set of hashes

                    for (auto&& activationToken : *request.activation_tokens()) {
                        SHA256Hash tokenHash;
                        ParsedActivationToken parsedToken;

                        int openRes = OpenActivationToken(*activationToken, &tokenHash, &parsedToken);
                        if (openRes != 0) {
                            return openRes;
                        }

                        if (parsedToken.Flags & ParsedActivationToken::FLAG_DISALLOW_PREAUTH) {
                            ESP_LOGW(TAG, "Activation token explicitly disallows preauthorization");
                            return 403;
                        }

                        tokenHashes.push_back(tokenHash);
                    }

                    std::vector<flatbuffers::Offset<PreauthorizationTokenResult>> tokenOffsets;

                    for (auto&& tokenHash : tokenHashes) {
                        int64_t existingTokenTimestamp = 0;
                        int64_t tokenExpiryTime = newTokenExpiryTime;
                        bool ignoreRazzia = false;

                        if (!m_PreauthRateLimiter.IsTokenHashAllowed(tokenHash, currentTime, &existingTokenTimestamp)) {
                            tokenExpiryTime = existingTokenTimestamp + m_Config.PreauthorizationGracePeriodMs;
                            if (currentTime > tokenExpiryTime) {
                                ESP_LOGW(TAG, "Activation token hash has already been used recently and is no longer valid, returning nothing");

                                tokenOffsets.push_back(CreatePreauthorizationTokenResult(
                                    fbb,
                                    PreauthorizationTokenStatus_RequestedTooOften
                                ));

                                continue;
                            }
                            else {
                                ESP_LOGW(TAG, "Activation token hash has already been used and can still be valid, returning token with adjusted validity");
                                ignoreRazzia = true;
                            }
                        }
                        else if (!isRazzia) {
                            m_PreauthRateLimiter.RegisterTokenHashUsed(tokenHash, currentTime);
                        }

                        if (!isRazzia || ignoreRazzia) {
                            auto preauthTokenBlob = m_TokenManager.CreatePreauthorizationToken(tokenHash, tokenExpiryTime);

                            tokenOffsets.push_back(CreatePreauthorizationTokenResult(
                                fbb,
                                PreauthorizationTokenStatus_Ok,
                                CreatePreauthorizationToken(fbb, fbb.CreateVector(preauthTokenBlob)),
                                tokenExpiryTime
                            ));
                        }
                        else {
                            tokenOffsets.push_back(CreatePreauthorizationTokenResult(
                                fbb,
                                PreauthorizationTokenStatus_RequestedDuringRazzia
                            ));
                        }
                    }

                    auto finishedTime = SystemTime::UptimeMillis();

                    // time approximately halfway between request and response, to allow for more accurate RTT estimation
                    auto halfTime = currentTime + (finishedTime - currentTime) / 2;

                    fbb.Finish(CreatePreauthorizationTokenResponseDirect(fbb, halfTime, &tokenOffsets));

                    return 200;
                }
            )
        );

        registry.RegisterServiceCallback(
            Operation_ActivateTicket,
            ApplicationServer::CreateOperationServiceFunc<TicketActivationRequest>(
                [&](const TicketActivationRequest& request, flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                    psram_string metadata;
                    {
                        std::lock_guard lock(m_DataMutex);
                        if (!m_HasData) {
                            return 503; // service unavailable
                        }
                        metadata = m_CurrentValidationMetadata;
                    }

                    const ActivationToken* activationToken = request.activation_token();

                    SHA256Hash tokenHash;
                    ParsedActivationToken parsedToken;
                    int openRes = OpenActivationToken(*activationToken, &tokenHash, &parsedToken);
                    if (openRes != 0) {
                        return openRes;
                    }

                    bool preauthTokenValid = false;
                    if (request.preauthorization_token()) {
                        auto preauthTokenBlob = request.preauthorization_token()->data();
                        auto preauthResult = m_TokenManager.VerifyPreauthorizationToken(ByteSpan(preauthTokenBlob->data(), preauthTokenBlob->size()), tokenHash, SystemTime::UptimeMillis());
                        if (preauthResult == PreauthorizationTokenManager::VerificationResult::OK) {
                            preauthTokenValid = true;
                        }
                        else if (preauthResult == PreauthorizationTokenManager::VerificationResult::TOKEN_EXPIRED) {
                            ESP_LOGW(TAG, "Preauthorization token has expired");
                        }
                        else {
                            ESP_LOGW(TAG, "Preauthorization token verification failed: %d", static_cast<int>(preauthResult));
                            return 403;
                        }
                    }

                    MOSTicketActivationParams activationParams{
                        .ActivateNowIfEarlier = request.activate_now_if_earlier(),
                        .ClientIntegrityAttested = preauthTokenValid,
                        .Zones = request.activation_zones() ? request.activation_zones()->str() : "",
                        .ClientAppID = request.activation_app_id()->str(),
                        .LwtMetadata = std::move(metadata)
                    };

                    if (request.activation_time()) {
                        activationParams.Time = FlatLwtToIso(*request.activation_time());;
                    }

                    MOSTicket activatedTicket;

                    int mosResult = m_MOSClient.ActivateTicket(parsedToken.TicketId, activationParams, &activatedTicket);
                    if (!MOSClient::IsStatusOK(mosResult)) {
                        ESP_LOGW(TAG, "MOS ticket activation failed with HTTP status %d", mosResult);
                        return mosResult;
                    }

                    LwtOffsetDateTime validSince = IsoToFlatLwt(activatedTicket.ValidSince);
                    LwtOffsetDateTime validUntil = IsoToFlatLwt(activatedTicket.ValidUntil);
                    LwtOffsetDateTime activatedAt = IsoToFlatLwt(activatedTicket.ActivationTime);

                    fbb.Finish(CreateTicketActivationResponse(
                        fbb,
                        fbb.CreateVector(activatedTicket.Payload.ETD),
                        fbb.CreateVector(activatedTicket.Payload.TOTPSeed),
                        &validSince,
                        &validUntil,
                        &activatedAt
                    ));

                    return 200;
                }
            )
        );

        registry.RegisterServiceCallback(
            Operation_SetRazzia,
            ApplicationServer::CreateOperationServiceFunc<SetRazziaRequest>(
                [&](const SetRazziaRequest& request, flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                    if (request.is_razzia()) {
                        ESP_LOGI(TAG, "Ticket inspector requested to enter razzia mode");
                    }
                    else {
                        ESP_LOGW(TAG, "Ticket inspector requested to exit razzia mode");
                    }

                    std::lock_guard lock(m_DataMutex);

                    if (SetLocalRazziaState(request.is_razzia())) {
                        PropagateRazziaStateToRCS(request.is_razzia());
                        UpdateValidationInfo();
                    }

                    if (request.is_razzia()) {
                        m_LastLocalRazziaOnTime = SystemTime::UptimeMicros();
                        m_RazziaOffTimer.Restart();
                    }
                    else {
                        m_RazziaOffTimer.Stop();
                    }

                    fbb.Finish(CreateSetRazziaResponse(fbb, RAZZIA_HEARTBEAT_MAX_SECONDS));

                    return 200;
                }
            ),
            CertRole::TICKET_INSPECTOR
        );
    }

    void TicketValidationService::ClearLocalRazziaIfExpired() {
        if (m_LastLocalRazziaOnTime > 0 && (m_IsRazzia & RAZZIA_LOCAL_BIT) != 0) {
            auto currentTime = SystemTime::UptimeMicros();
            if (currentTime - m_LastLocalRazziaOnTime > RAZZIA_HEARTBEAT_MAX_SECONDS * 1000 * 1000) {
                ESP_LOGI(TAG, "Local razzia state expired, clearing it");
                SetLocalRazziaState(false);
                m_LastLocalRazziaOnTime = 0; //reset to avoid infinite recursion
                PropagateRazziaStateToRCS(false);
                UpdateValidationInfo();
            }
        }
    }

    bool TicketValidationService::IsRazzia()
    {
        std::lock_guard lock(m_DataMutex);

        return IsRazziaNoLock();
    }

    psram_string TicketValidationService::GetCurrentValidationMetadata()
    {
        std::lock_guard lock(m_DataMutex);

        return m_CurrentValidationMetadata;
    }

    bool TicketValidationService::IsRazziaNoLock()
    {
        return m_IsRazzia != 0;
    }

    bool TicketValidationService::SetRazziaBit(int bit, bool value)
    {
        if (value) {
            return (m_IsRazzia.fetch_or(bit) & bit) == 0; //changed if not set
        }
        else {
            return m_IsRazzia.fetch_and(~bit) & bit; // changed if was set
        }
    }

    bool TicketValidationService::SetLocalRazziaState(bool isRazzia)
    {
        return SetRazziaBit(RAZZIA_LOCAL_BIT, isRazzia);
    }

    void TicketValidationService::PropagateRazziaStateToRCS(bool isRazzia)
    {
        if (m_RCS) {
            if (isRazzia) {
                m_RCS->StartRazzia();
            }
            else {
                m_RCS->StopRazzia();
            }
        }
    }

    void TicketValidationService::OnChanged(const TripRouteInfo* result)
    {
        std::lock_guard lock(m_DataMutex);

        if (result) {
            m_CurTripDelay = result->trip()->delay();

            if (!m_HasTVSData) {
                ESP_LOGI(TAG, "No TVS data available (yet), generating ticket validation info from TripRouteInfo");

                // if we do not have a TVS subscription or TVS is not supported, we must generate this data from TripRouteInfo
                auto curStop = result->stops()->Get(result->trip()->current_departure_stop()->sequence_id());

                if (curStop->dep_time()) {
                    m_TimeForTicketValidityStart = curStop->dep_time()->local_instant() + m_CurTripDelay * 60;
                }
                else {
                    ESP_LOGE(TAG, "Current stop has no dep. time, cannot determine ticket validity start time");
                    m_TimeForTicketValidityStart = 0;
                }
                if (curStop->tariff_zones()) {
                    m_TariffZonesForValidation = GetTariffZonesOnlyMyTariffSystem(curStop->tariff_zones()->str());
                }
                else {
                    m_TariffZonesForValidation.clear();
                }
            }
            else {
                ESP_LOGI(TAG, "TVS data already present, ignoring TripRouteInfo for ticket validation");
            }

            m_NextTariffZonesFromRoute.clear();
            for (auto i = result->trip()->current_departure_stop()->sequence_id() + 1; i < result->stops()->size(); ++i) {
                auto stop = result->stops()->Get(i);
                if (stop->tariff_zones()) {
                    m_NextTariffZonesFromRoute.push_back(GetTariffZonesOnlyMyTariffSystem(stop->tariff_zones()->str()));
                }
            }

            // the "next tariff zones" have to always be provided from TripRouteInfo, as TVS does not have anything like this
            m_NextTariffZonesForValidation = ReduceNextTariffZones(m_NextTariffZonesFromRoute, m_TariffZonesForValidation);
        }
        else {
            if (!m_TVS) {
                m_TimeForTicketValidityStart = 0;
            }
        }

        UpdateValidationInfo();
    }

    void TicketValidationService::OnChanged(const SubscriberTVS::CurrentTariffStop* result) {
        std::lock_guard lock(m_DataMutex);

        if (result) {
            ESP_LOGI(TAG, "Updating ticket validation info from TVS CurrentTariffStop data");
            m_HasTVSData = true;
            auto&& scheduledDep = result->CurrentTariffStop.DepartureScheduled;
            if (scheduledDep && !scheduledDep->Value.empty()) {
                m_TimeForTicketValidityStart = LocalDateTime::parse(scheduledDep->Value).to_utc_epoch_seconds() + m_CurTripDelay * 60;
            }
            else {
                m_TimeForTicketValidityStart = 0;
            }
            m_TariffZonesForValidation = GetTariffZonesOnlyMyTariffSystem(TripInformationService::BuildTariffZonesString(result->CurrentTariffStop));
            m_NextTariffZonesForValidation = ReduceNextTariffZones(m_NextTariffZonesFromRoute, m_TariffZonesForValidation);
        }
        else {
            // use m_TimeForTicketValidityStart and m_TariffZonesForValidation from TripRouteInfo, if available
            m_HasTVSData = false;
        }

        UpdateValidationInfo();
    }

    void TicketValidationService::OnChanged(const SubscriberTVS::RazziaState* razziaState) {
        std::lock_guard lock(m_DataMutex);

        bool isRazzia = razziaState && razziaState->RazziaState == TVS::TicketRazziaInformationEnumeration::razzia;

        SetRazziaBit(RAZZIA_TVS_BIT, isRazzia);
        UpdateValidationInfo();
    }

    std::string TicketValidationService::GetTariffZonesOnlyMyTariffSystem(const std::string& tariffZones) const {
        auto presentSystems = TripInformationService::GetSpecifiedTariffSystemIDs(tariffZones);
        if (presentSystems.empty()) {
            return tariffZones;
        }
        return TripInformationService::GetZonesInTariffSystem(tariffZones, m_Config.TariffSystemID);
    }

    std::unordered_set<std::string> ParseSingleSystemTariffZoneSet(const std::string& tariffZones) {
        std::unordered_set<std::string> zoneSet;
        for (auto zone : str_split(tariffZones, ',')) {
            zoneSet.insert(zone);
        }
        return zoneSet;
    }

    std::string TicketValidationService::ReduceNextTariffZones(const std::vector<std::string>& nextTariffZones, const std::string& curTariffZones)
    {
        if (nextTariffZones.empty()) {
            return {};
        }

        auto curZoneSet = ParseSingleSystemTariffZoneSet(curTariffZones);
        std::string reducedZones;

        for (auto&& nextZones : nextTariffZones) {
            for (auto&& nextZone : ParseSingleSystemTariffZoneSet(nextZones)) {
                if (curZoneSet.insert(nextZone).second) {
                    if (!reducedZones.empty()) {
                        reducedZones += ",";
                    }
                    reducedZones += nextZone;
                }
            }
        }

        return reducedZones;
    }

    void TicketValidationService::UpdateValidationInfo()
    {
        auto oldTripKey = GetCurrentTripKeyNoLock();

        ResetValidationInfo();

        auto currentStopFbb = PSRAMFlatBufferBuilder(256);

        auto tripInfoResult = m_TripInfoService.GetTripStateInfoEx(&m_ValidationInfoFBB, &currentStopFbb);
        auto tsi = tripInfoResult.OfsTripState;

        if (tsi.IsNull()) {
            ESP_LOGW(TAG, "TripStateInfo is null, cannot update ticket validation info");
        }
        if (m_TimeForTicketValidityStart == 0) {
            ESP_LOGW(TAG, "Time for ticket validity start not set, cannot update ticket validation info");
        }

        if (tsi.IsNull() || m_TimeForTicketValidityStart == 0) {
            return;
        }

        LwtLocalDateTime validityStart(m_TimeForTicketValidityStart);

        FinishValidationInfo(CreateTicketValidationInfo(
            m_ValidationInfoFBB,
            tsi,
            &validityStart,
            m_ValidationInfoFBB.CreateString(m_TariffZonesForValidation),
            m_ValidationInfoFBB.CreateString(m_NextTariffZonesForValidation),
            // do not call IsRazziaNoLock() here, as we do not want to trigger a recursive data rebuild.
            // just read the raw value currently present in the field.
            m_IsRazzia != 0
        ));

        auto newTripKey = GetCurrentTripKeyNoLock();

        auto validationInfo = GetValidationInfo();
        psram_vector<psram_string> metadataParts;
        if (!newTripKey.empty()) {
            metadataParts.emplace_back("TK:" + psram_string(newTripKey));
        }
        auto curStopId = validationInfo->trip()->current_departure_stop()->global_ref_id();
        if (curStopId) {
            metadataParts.emplace_back("S:" + std::to_string(curStopId));
        }
        auto ofsStopInfo = tripInfoResult.OfsCurrentStop;
        if (!ofsStopInfo.IsNull()) {
            currentStopFbb.Finish(ofsStopInfo);
            const TripStopInfo* stopInfo = flatbuffers::GetRoot<TripStopInfo>(currentStopFbb.GetBufferPointer());
            const char* ttTimeKey;
            const LwtLocalDateTime* ttTime = nullptr;
            if (stopInfo->dep_time()) {
                ttTimeKey = "TTD";
                ttTime = stopInfo->dep_time();
            }
            else if (stopInfo->arr_time()) {
                ttTimeKey = "TTA";
                ttTime = stopInfo->arr_time();
            }
            if (ttTime) {
                metadataParts.emplace_back(psram_string(ttTimeKey) + ":" + psram_string(LocalDateTime::of_utc_epoch_seconds(ttTime->local_instant()).to_string()));
            }
        }
        auto locationState = validationInfo->trip()->location_state();
        if (locationState == LocationState_AtStop) {
            metadataParts.push_back("AS");
        }
        m_CurrentValidationMetadata = str_join(metadataParts.begin(), metadataParts.end(), "|");

        if (newTripKey != oldTripKey) {
            ESP_LOGI(TAG, "Trip changed, invalidating preauth rate limit");
            m_PreauthRateLimiter.InvalidateAll();
        }
    }

    void TicketValidationService::ResetValidationInfo()
    {
        m_ValidationInfoFBB.Clear();
        m_HasData = false;
    }

    void TicketValidationService::FinishValidationInfo(flatbuffers::Offset<TicketValidationInfo> data)
    {
        m_ValidationInfoFBB.Finish(data);
        m_HasData = true;
    }

    const TicketValidationInfo* TicketValidationService::GetValidationInfo() const
    {
        if (!m_HasData) {
            return nullptr;
        }
        return flatbuffers::GetRoot<TicketValidationInfo>(m_ValidationInfoFBB.GetBufferPointer());
    }

    std::string TicketValidationService::GetCurrentTripKeyNoLock() const
    {
        if (m_HasData) {
            auto tvi = GetValidationInfo();
            if (tvi->trip()) {
                auto lineNum = tvi->trip()->trip()->line()->global_ref_id();
                auto tripNum = tvi->trip()->trip()->global_ref_id();

                if (!tripNum) {
                    return std::to_string(lineNum);
                }
                else {
                    return std::to_string(lineNum) + "/" + std::to_string(tripNum);
                }
            }
        }

        return "";
    }

    std::string TicketValidationService::GetCurrentTripKey()
    {
        std::lock_guard lock(m_DataMutex);
        return GetCurrentTripKeyNoLock();
    }

    TicketPreauthRateLimiter::TicketPreauthRateLimiter(size_t capacity, int64_t maxAgeMs) :
        m_Capacity{ capacity },
        m_MaxAgeMs{ maxAgeMs }
    {
    }

    void TicketPreauthRateLimiter::InvalidateAll()
    {
        m_Entries.clear();
    }

    void TicketPreauthRateLimiter::InvalidateOldEntries(int64_t currentTime)
    {
        while (!m_Entries.empty()) {
            auto& entry = m_Entries.front();
            if (currentTime - entry.TimestampMs > m_MaxAgeMs) {
                m_Entries.pop_front();
            }
            else {
                break;
            }
        }
    }

    bool TicketPreauthRateLimiter::IsTokenHashAllowed(const SHA256HashView& tokenHash, int64_t currentTime, int64_t* pBlockingTokenTimestamp)
    {
        InvalidateOldEntries(currentTime);

        for (auto&& entry : m_Entries) {
            if (std::equal(entry.TokenHash.begin(), entry.TokenHash.end(), tokenHash.begin())) {
                if (pBlockingTokenTimestamp) {
                    *pBlockingTokenTimestamp = entry.TimestampMs;
                }
                return false;
            }
        }

        return true;
    }

    void TicketPreauthRateLimiter::RegisterTokenHashUsed(const SHA256HashView& tokenHash, int64_t currentTime)
    {
        InvalidateOldEntries(currentTime);

        if (m_Entries.size() >= m_Capacity) {
            m_Entries.pop_front();
        }

        Entry newEntry;
        std::copy(tokenHash.begin(), tokenHash.end(), newEntry.TokenHash.begin());
        newEntry.TimestampMs = currentTime;

        m_Entries.push_back(std::move(newEntry));
    }

    int TicketValidationService::OpenActivationToken(const ActivationToken& activationToken, SHA256Hash* pHash, ParsedActivationToken* pParsedToken)
    {
        constexpr size_t ACTIVATION_TOKEN_MIN_SIZE = sizeof(uint16_t);

        auto&& data = activationToken.data();
        if (data->size() < ACTIVATION_TOKEN_MIN_SIZE) {
            ESP_LOGW(TAG, "Invalid activation token hash size: %zu (expected at least %zu)", data->size(), ACTIVATION_TOKEN_MIN_SIZE);
            return 400;
        }

        auto dataStart = data->data();

        uint16_t tokenVersion = BC::ToUInt16(dataStart);
        if (tokenVersion > 1) {
            ESP_LOGW(TAG, "Unsupported activation token version: %u", tokenVersion);
            return 400;
        }

        ByteSpan tokenData;
        ByteSpan tokenSignature;
        uint32_t keyId;

        if (!SecureToken::ParseSignedToken(flatbuffers::make_span(activationToken.data()), &tokenData, &tokenSignature, &keyId)) {
            ESP_LOGW(TAG, "Failed to parse activation token signature");
            return 400;
        }

        SHA256Hash tokenHash = HashActivationToken(tokenData);
        *pHash = tokenHash;

        if (!m_TicketVerifier.VerifyHashSignature(tokenHash, MBEDTLS_MD_SHA256, tokenSignature, keyId)) {
            ESP_LOGW(TAG, "Activation token signature verification failed");
            return 403;
        }

        *pParsedToken = ParseActivationToken(tokenData);

        return 0;
    }

    SHA256Hash TicketValidationService::HashActivationToken(const ByteSpan& token)
    {
        return MessageDigest::SHA256(token);
    }

    TicketValidationService::ParsedActivationToken TicketValidationService::ParseActivationToken(const ByteSpan& tokenData)
    {
        BC::InputStream in(tokenData.data());

        in.ReadUInt16(); // version, currently not used here

        ParsedActivationToken parsed;
        parsed.TicketId = in.ReadUInt64();
        parsed.Flags = in.ReadUInt32();

        return parsed;
    }
}