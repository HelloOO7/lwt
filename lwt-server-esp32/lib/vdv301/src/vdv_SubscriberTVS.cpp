#include "vdv_SubscriberTVS.h"
#include "esp_log.h"

namespace vdv301
{
    static constexpr const char* TAG = "SubscriberTVS";

    SubscriberTVS::SubscriberTVS(ServiceDiscovery& sd, Operation subscribedOps) :
        SubscriberHttp(
            sd,
            "TicketValidationService",
            ServiceDiscovery::QueryBuilder()
            .FilterInstanceName("TicketValidationService*")
            .FilterTxtRecord("ver", "2.2CZ1.0")
            .Build(),
            std::to_underlying(subscribedOps),
            4096 | EventQueue::STACK_PSRAM_BIT
        )
    {
    }

    std::string SubscriberTVS::GetOperationName(OperationIDType operation) const
    {
        switch (static_cast<Operation>(operation))
        {
        case Operation::GetCurrentStopPoint:
            return "GetCurrentStopPoint";
        case Operation::GetRazzia:
            return "GetRazzia";
        case Operation::GetCurrentLine:
            return "GetCurrentLine";
        case Operation::GetVehicleData:
            return "GetVehicleData";
        case Operation::RetrieveTripData:
            return "RetrieveTripData";
        default:
            return "UnknownOperation";
        }
    }

    void SubscriberTVS::OnOperationResult(const OperationResult& result)
    {
        using namespace IBIS_IP_TicketValidationService_V2_2;

        try {
            switch (result.GetOperationID<Operation>())
            {
            case Operation::GetRazzia:
            {
                TicketValidationService_GetRazziaResponseStructure razziaResp;
                load_data(result.GetResult().c_str(), razziaResp);
                if (razziaResp.RazziaData) {
                    m_LastRazziaResp = std::move(*razziaResp.RazziaData);
                    ESP_LOGI(TAG, "Updated Razzia data: state=%s timestamp=%s",
                        m_LastRazziaResp.RazziaState == TicketRazziaInformationEnumeration::razzia ? "RAZZIA" : "NO RAZZIA",
                        m_LastRazziaResp.TimeStamp.Value.c_str());
                }
                else if (razziaResp.OperationErrorMessage) {
                    ESP_LOGE(TAG, "Error in Razzia response (keeping old data): %s", razziaResp.OperationErrorMessage->Value.c_str());
                }
                break;
            }
            case Operation::GetCurrentStopPoint:
            {
                TicketValidationService_GetCurrentTariffStopResponseStructure stopResp;
                load_data(result.GetResult().c_str(), stopResp);
                if (stopResp.CurrentTariffStopData) {
                    m_CurTariffStop = std::move(*stopResp.CurrentTariffStopData);
                    ESP_LOGI(TAG, "Updated CurrentStopPoint data: stop=%s timestamp=%s",
                        m_CurTariffStop.CurrentTariffStop.StopRef.Value.c_str(),
                        m_CurTariffStop.TimeStamp.Value.c_str());
                }
                else if (stopResp.OperationErrorMessage) {
                    ESP_LOGE(TAG, "Error in CurrentStopPoint response (keeping old data): %s", stopResp.OperationErrorMessage->Value.c_str());
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