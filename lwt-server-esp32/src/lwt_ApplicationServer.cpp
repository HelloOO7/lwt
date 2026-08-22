#include "lwt_ApplicationServer.h"

#include "packet_generated.h"
#include "operations_generated.h"
#include "flatbuffer_util.h"
#include "esp_log.h"
#include <cstdio>
#include "lwt_CertRoleInterceptor.h"

namespace lwt {

    ApplicationServer::ApplicationServer(ServiceRegistry& serviceRegistry) :
        m_ServiceRegistry(serviceRegistry)
    {

    }

    lwtp::PacketData ApplicationServer::ServeRequest(lwtp::SocketSession& session, const lwtp::PacketData& request) {
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
            Operation operationId = (Operation)requestFb->operation_id();
            if (m_ServiceRegistry.IsServiceRegistered(operationId)) {
                CertRole ownedRoles = CertRole::NONE;
                {
                    int ownedRolesRaw = 0;
                    if (session.GetTag(&CERT_ROLE_MASK_TAG, &ownedRolesRaw)) {
                        ownedRoles = (CertRole)ownedRolesRaw;
                    }
                }

                if (!m_ServiceRegistry.CheckOperationAccess(operationId, ownedRoles)) {
                    ESP_LOGW("ApplicationServer", "Access denied for operation ID %u; owned roles: %u", requestFb->operation_id(), ownedRoles);
                    statusCode = 403; // Forbidden
                }
                else {
                    try {
                        const auto& operationService = m_ServiceRegistry.GetService(operationId);
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