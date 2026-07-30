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

namespace lwt {

    static constexpr const char* TAG = "TicketValidationService";

    using namespace vdv301;

    TicketValidationService::TicketValidationService(const TicketValidationConfig& config, PreauthorizationTokenManager& tokenManager, TicketSignatureVerifier& ticketVerifier, TripInformationService& tripInfoService, SubscriberTVS* tvsOpt) :
        m_Config{ config },
        m_TokenManager{ tokenManager },
        m_PreauthRateLimiter(1000, 45LL * 60 * 1000 * 1000), // 1000 entries, 45 minutes max age
        m_TicketVerifier{ ticketVerifier },
        m_TripInfoService{ tripInfoService },
        m_TVS{ tvsOpt }
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
                    if (IsRazzia()) {
                        ESP_LOGW(TAG, "Refusing to create preauthorization token during razzia");
                        return 403;
                    }

                    std::lock_guard lock(m_TokenGeneratorMutex);

                    constexpr size_t ACTIVATION_TOKEN_HASH_SIZE = 32;
                    constexpr size_t ACTIVATION_TOKEN_MIN_SIZE = sizeof(uint16_t) + ACTIVATION_TOKEN_HASH_SIZE + sizeof(uint32_t) + sizeof(uint16_t);

                    auto currentTime = esp_timer_get_time();
                    auto expiryTime = currentTime + m_Config.PreauthorizationGracePeriodUs;

                    std::vector<SHA256HashView> tokenHashes; // can be a view, as it looks inside the request flatbuffer

                    // validate and create input set of hashes

                    for (auto&& activationTokenHash : *request.activation_token_hashes()) {
                        auto&& data = activationTokenHash->data();
                        if (data->size() < ACTIVATION_TOKEN_MIN_SIZE) {
                            ESP_LOGW(TAG, "Invalid activation token hash size: %zu (expected at least %zu)", data->size(), ACTIVATION_TOKEN_MIN_SIZE);
                            return 400;
                        }

                        BitConverter<std::endian::big>::InputStream in(data->data());

                        uint16_t tokenVersion = in.ReadUInt16();
                        if (tokenVersion > 0) {
                            ESP_LOGW(TAG, "Unsupported activation token version: %u", tokenVersion);
                            return 400;
                        }

                        SHA256HashView tokenHash = in.ReadBytes<ACTIVATION_TOKEN_HASH_SIZE>();
                        uint32_t keyId = in.ReadUInt32();
                        uint16_t signatureLength = in.ReadUInt16();

                        if (ACTIVATION_TOKEN_MIN_SIZE + signatureLength != data->size()) {
                            ESP_LOGW(TAG, "Invalid activation token hash size: %zu (expected %zu for signature length %u)", data->size(), ACTIVATION_TOKEN_MIN_SIZE + signatureLength, signatureLength);
                            return 400;
                        }

                        auto tokenSignature = in.ReadBytes(signatureLength);

                        if (!m_TicketVerifier.VerifySignature(tokenHash, MBEDTLS_MD_SHA256, tokenSignature, keyId)) {
                            return 403;
                        }

                        tokenHashes.push_back(tokenHash);
                    }

                    std::vector<flatbuffers::Offset<PreauthorizationToken>> tokenOffsets;

                    for (auto&& tokenHash : tokenHashes) {
                        if (!m_PreauthRateLimiter.IsTokenHashAllowed(tokenHash, currentTime)) {
                            ESP_LOGW(TAG, "Activation token hash has already been used recently, refusing to create preauthorization token");

                            tokenOffsets.push_back(CreatePreauthorizationToken(
                                fbb,
                                PreauthorizationTokenStatus_RequestedTooOften
                            ));
                        }
                        else {
                            m_PreauthRateLimiter.RegisterTokenHashUsed(tokenHash, currentTime);

                            auto preauthTokenBlob = m_TokenManager.CreatePreauthorizationToken(tokenHash, expiryTime);

                            tokenOffsets.push_back(CreatePreauthorizationToken(
                                fbb,
                                PreauthorizationTokenStatus_Ok,
                                fbb.CreateVector(preauthTokenBlob)
                            ));
                        }
                    }

                    auto finishedTime = esp_timer_get_time();

                    // time approximately halfway between request and response, to allow for more accurate RTT estimation
                    auto halfTime = currentTime + (finishedTime - currentTime) / 2;

                    fbb.Finish(CreatePreauthorizationTokenResponseDirect(fbb, halfTime / 1000, expiryTime / 1000, &tokenOffsets));

                    return 200;
                }
            )
        );
    }

    bool TicketValidationService::IsRazzia()
    {
        std::lock_guard lock(m_DataMutex);
        return m_IsRazzia;
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

                m_TimeForTicketValidityStart = curStop->dep_time() + m_CurTripDelay * 60;
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

        bool ok = false;

        if (result) {
            ESP_LOGI(TAG, "Updating ticket validation info from TVS CurrentTariffStop data");
            m_HasTVSData = true;
            auto&& scheduledDep = result->CurrentTariffStop.DepartureScheduled;
            if (scheduledDep && !scheduledDep->Value.empty()) {
                m_TimeForTicketValidityStart = LocalDateTime::parse(scheduledDep->Value).to_epoch_seconds() + m_CurTripDelay * 60;
                ok = true;
            }
            m_TariffZonesForValidation = GetTariffZonesOnlyMyTariffSystem(TripInformationService::BuildTariffZonesString(result->CurrentTariffStop));
            m_NextTariffZonesForValidation = ReduceNextTariffZones(m_NextTariffZonesFromRoute, m_TariffZonesForValidation);
        }
        else {
            m_HasTVSData = false;
        }

        if (!ok) {
            m_TimeForTicketValidityStart = 0;
        }

        UpdateValidationInfo();
    }

    void TicketValidationService::OnChanged(const SubscriberTVS::RazziaState* razziaState) {
        std::lock_guard lock(m_DataMutex);

        m_IsRazzia = razziaState && razziaState->RazziaState == IBIS_IP_TicketValidationService_V2_2::TicketRazziaInformationEnumeration::razzia;

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
        auto oldTripKey = GetCurrentTripKey();

        ResetValidationInfo();

        auto tsi = m_TripInfoService.GetTripStateInfo(m_ValidationInfoFBB);

        if (tsi.IsNull() || m_TimeForTicketValidityStart == 0) {
            return;
        }

        FinishValidationInfo(CreateTicketValidationInfo(
            m_ValidationInfoFBB,
            tsi,
            m_TimeForTicketValidityStart,
            m_ValidationInfoFBB.CreateString(m_TariffZonesForValidation),
            m_ValidationInfoFBB.CreateString(m_NextTariffZonesForValidation),
            m_IsRazzia
        ));

        if (GetCurrentTripKey() != oldTripKey) {
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

    std::string TicketValidationService::GetCurrentTripKey()
    {
        if (m_HasData) {
            auto tvi = flatbuffers::GetRoot<TicketValidationInfo>(m_ValidationInfoFBB.GetBufferPointer());
            if (tvi->trip()) {
                auto lineNum = tvi->trip()->trip()->line()->global_ref_id();
                auto tripNum = tvi->trip()->trip()->global_ref_id();

                return std::to_string(lineNum) + "/" + std::to_string(tripNum);
            }
        }

        return "";
    }

    TicketPreauthRateLimiter::TicketPreauthRateLimiter(size_t capacity, int64_t maxAgeUs) :
        m_Capacity{ capacity },
        m_MaxAgeUs{ maxAgeUs }
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
            if (currentTime - entry.TimestampUs > m_MaxAgeUs) {
                m_Entries.pop_front();
            }
            else {
                break;
            }
        }
    }

    bool TicketPreauthRateLimiter::IsTokenHashAllowed(const SHA256HashView& tokenHash, int64_t currentTime)
    {
        InvalidateOldEntries(currentTime);

        for (auto&& entry : m_Entries) {
            if (std::equal(entry.TokenHash.begin(), entry.TokenHash.end(), tokenHash.begin())) {
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
        newEntry.TimestampUs = currentTime;

        m_Entries.push_back(std::move(newEntry));
    }
}