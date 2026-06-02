#include "lwt_ApplicationServer.h"

#include "packet_generated.h"
#include "operations_generated.h"
#include "flatbuffer_util.h"
#include "esp_log.h"
#include <cstdio>

namespace lwt {

    ApplicationServer::ApplicationServer(ServiceRegistry& serviceRegistry) :
        m_ServiceRegistry(serviceRegistry)
    {

    }

    lwtp::PacketData ApplicationServer::ServeRequest(const lwtp::PacketData& request) {
        // debug
        printf("request: ");
        for (auto& byte : request) {
            printf("%02x ", byte);
        }
        printf("\n");

        const RequestPacket* requestFb = GetAndVerify<RequestPacket>(request);
        auto responseBuilder = PSRAMFlatBufferBuilder();
        
        int32_t statusCode = 200;
        flatbuffers::Offset<flatbuffers::Vector<uint8_t>> dataOffset = 0;

        if (!requestFb) {
            ESP_LOGE("ApplicationServer", "Malformed request packet flatbuffer");
            statusCode = 400; // Bad Request
        }
        else {
            if (m_ServiceRegistry.IsServiceRegistered((Operation)requestFb->operation_id())) {
                try {
                    const auto& operationService = m_ServiceRegistry.GetService((Operation)requestFb->operation_id());
                    auto opResult = operationService(*requestFb);
                    statusCode = opResult.GetStatus();
                    if (!opResult.GetResponseData().empty()) {
                        dataOffset = responseBuilder.CreateVector(std::move(opResult.GetResponseData()));
                    }
                }
                catch (const std::exception& ex) {
                    ESP_LOGE("ApplicationServer", "Error processing request for operation ID %u - %s", requestFb->operation_id(), ex.what());
                    statusCode = 500; // Internal Server Error
                }
            }
            else {
                ESP_LOGW("ApplicationServer", "No service registered for operation ID %u", requestFb->operation_id());
                statusCode = 404; // Not Found
            }
        }

        responseBuilder.Finish(CreateResponsePacket(responseBuilder, statusCode, dataOffset));        

        return SerializeFlatBuffer(responseBuilder);
    }
}