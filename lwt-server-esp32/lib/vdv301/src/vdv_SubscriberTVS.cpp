#include "vdv_SubscriberTVS.h"
#include "esp_log.h"
#include "FNVHash.h"
#include "vdv_Utility.h"
#include <regex>
#include "NewAndDelete.h"

namespace vdv301
{
    static constexpr const char* TAG = "SubscriberTVS";

    SubscriberTVS::SubscriberTVS(ServiceDiscovery& sd, Operation subscribedOps, int taskPriority) :
        SubscriberHttp(
            sd,
            "TicketValidationService",
            ServiceDiscovery::QueryBuilder()
            .FilterInstanceName("TicketValidationService*")
            // mDNS version is 2.2, actual data is a mix of 2.2 and 2.3CZ1.0, has to be regex'd (see below)
            .FilterTxtRecord("ver", "2.2")
            .Build(),
            std::to_underlying(subscribedOps),
            8192,
            taskPriority
        )
    {
    }

    void SubscriberTVS::ObserveRazziaState(Observer<RazziaState>& observer)
    {
        Observable<RazziaState>::AddObserver(observer);
    }

    void SubscriberTVS::ObserveCurrentTariffStop(Observer<CurrentTariffStop>& observer)
    {
        Observable<CurrentTariffStop>::AddObserver(observer);
    }

    void SubscriberTVS::RemoveObserver(Observer<RazziaState>& observer)
    {
        Observable<RazziaState>::RemoveObserver(observer);
    }

    void SubscriberTVS::RemoveObserver(Observer<CurrentTariffStop>& observer)
    {
        Observable<CurrentTariffStop>::RemoveObserver(observer);
    }

    std::string SubscriberTVS::GetOperationName(OperationIDType operation) const
    {
        switch (static_cast<Operation>(operation))
        {
        case Operation::CurrentTariffStop:
            return "CurrentTariffStop";
        case Operation::Razzia:
            return "Razzia";
        case Operation::CurrentLine:
            return "CurrentLine";
        case Operation::VehicleData:
            return "VehicleData";
        case Operation::TripData:
            return "TripData";
        default:
            return "UnknownOperation";
        }
    }

    void SubscriberTVS::OnOperationResult(const OperationResult& result)
    {
        using namespace TVS;

        try {
            switch (result.GetOperationID<Operation>())
            {
            case Operation::Razzia:
            {
                auto hash = HashResponseWithoutTimestamp(result.GetResult());
                if (hash != m_LastRazziaRespHash) {
                    TicketValidationService_GetRazziaResponseStructure razziaResp;
                    {
                        UseHeapCaps<MALLOC_CAP_SPIRAM> usePsram;
                        load_data(result.GetResult().c_str(), razziaResp);
                    }
                    if (razziaResp.RazziaData) {
                        m_LastRazziaResp = std::move(*razziaResp.RazziaData);
                        ESP_LOGI(TAG, "Updated Razzia data: state=%s timestamp=%s",
                            m_LastRazziaResp.RazziaState == TicketRazziaInformationEnumeration::razzia ? "RAZZIA" : "NO RAZZIA",
                            m_LastRazziaResp.TimeStamp.Value.c_str());

                        m_LastRazziaRespHash = hash;
                        Observable<RazziaState>::NotifyObservers(&m_LastRazziaResp);
                    }
                    else if (razziaResp.OperationErrorMessage) {
                        ESP_LOGE(TAG, "Error in Razzia response (keeping old data): %s", razziaResp.OperationErrorMessage->Value.c_str());
                    }
                }
                break;
            }
            case Operation::CurrentTariffStop:
            {
                auto hash = HashResponseWithoutTimestamp(result.GetResult());
                if (hash != m_LastCurTariffStopHash) {
                    TicketValidationService_GetCurrentTariffStopResponseStructure stopResp;
                    {
                        UseHeapCaps<MALLOC_CAP_SPIRAM> usePsram;
                        load_data(result.GetResult().c_str(), stopResp);
                    }
                    if (stopResp.CurrentTariffStopData) {
                        m_CurTariffStop = std::move(*stopResp.CurrentTariffStopData);
                        ESP_LOGI(TAG, "Updated CurrentTariffStop data: stop=%s timestamp=%s",
                            m_CurTariffStop.CurrentTariffStop.StopRef.Value.c_str(),
                            m_CurTariffStop.TimeStamp.Value.c_str());

                        m_LastCurTariffStopHash = hash;
                        Observable<CurrentTariffStop>::NotifyObservers(&m_CurTariffStop);
                    }
                    else if (stopResp.OperationErrorMessage) {
                        ESP_LOGE(TAG, "Error in CurrentTariffStop response (keeping old data): %s", stopResp.OperationErrorMessage->Value.c_str());
                    }
                }
                break;
            }
            default:
                break;
            }
        }
        catch (const std::exception& e) {
            ESP_LOGE(TAG, "Exception while processing operation result: %s", e.what());
        }
    }
}