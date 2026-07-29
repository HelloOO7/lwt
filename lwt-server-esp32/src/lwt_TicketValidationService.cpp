#include "lwt_TicketValidationService.h"

#include <unordered_set>
#include "ISO8601.h"
#include "operations_generated.h"
#include "lwt_ApplicationServer.h"
#include "StringExtensions.h"
#include "esp_log.h"

namespace lwt {

    static constexpr const char* TAG = "TicketValidationService";

    using namespace vdv301;

    TicketValidationService::TicketValidationService(const std::string& tariffSystemId, TripInformationService& tripInfoService, SubscriberTVS* tvsOpt) :
        m_TariffSystemID{ tariffSystemId },
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
            } else {
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
        } else {
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
        return TripInformationService::GetZonesInTariffSystem(tariffZones, m_TariffSystemID);
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
}