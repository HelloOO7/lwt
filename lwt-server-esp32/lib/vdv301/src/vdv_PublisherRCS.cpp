#include "vdv_PublisherRCS.h"
#include "NewAndDelete.h"
#include <iostream>
#include "esp_log.h"

namespace vdv301 {

    static constexpr const char* TAG = "PublisherRCS";

    using namespace IBIS_IP_RemoteControlService_V2_3CZ1_0;

    PublisherRCS::PublisherRCS(ServiceDiscovery& sd, int taskPriority) :
        PublisherHttp(sd, "RemoteControlService", "2.3CZ1.0", 2048, taskPriority)
    {
        SetOperationEnabled((OperationIDType)Operation::AllData, true);
    }

    PublisherRCS::~PublisherRCS()
    {
    }

    void PublisherRCS::StartRazzia() {
        SendSignal(RemoteControlMessageTypeEnumeration::StartRazzia);
    }

    void PublisherRCS::StopRazzia() {
        SendSignal(RemoteControlMessageTypeEnumeration::StopRazzia);
    }

    void PublisherRCS::SendSignal(RCS::RemoteControlMessageTypeEnumeration type, std::optional<std::string> parameter) {
        psram_string content;

        {
            UseHeapCaps<MALLOC_CAP_SPIRAM> usePsram;

            RemoteControlService_GetAllDataResponseStructure response;
            response.MessageContent.MessageType = type;
            if (parameter) {
                response.MessageContent.MessageParameter = IBIS_IP_string{ *parameter };
            }

            content = save_data(response);
        }

        ESP_LOGI(TAG, "Sending signal to RCS");
        std::cout << content << std::endl;

        PublishData((OperationIDType)Operation::AllData, std::move(content), PublishMode::ONESHOT);
    }

    std::string PublisherRCS::GetOperationName(OperationIDType operation) const {
        switch ((Operation)operation) {
        case Operation::AllData:
            return "AllData";
        default:
            return "Unknown";
        }
    }
}