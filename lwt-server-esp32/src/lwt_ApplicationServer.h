#pragma once

#include "lwtp_Server.h"
#include "lwt_ServiceRegistry.h"
#include "flatbuffers/flatbuffers.h"
#include "flatbuffer_util.h"

namespace lwt {

    class ApplicationServer : public lwtp::Server {
    private:
        ServiceRegistry& m_ServiceRegistry;

    public:
        ApplicationServer(ServiceRegistry& serviceRegistry);

        lwtp::PacketData ServeRequest(const lwtp::PacketData& request) override;

        template<typename TRequestPacket, typename TOperationFunc>
        static OperationFunction CreateOperationServiceFunc(TOperationFunc&& service) {
            return
                [service = std::forward<TOperationFunc>(service)]
                (const RequestPacket& request) -> OperationResult
                {
                    if constexpr (std::is_same_v<TRequestPacket, void>) {
                        auto responseFbBuilder = PSRAMFlatBufferBuilder();

                        int result = service(responseFbBuilder);
                        auto innerData = SerializeFlatBuffer(responseFbBuilder);

                        return OperationResult(result, std::move(innerData));
                    }
                    else {
                        const TRequestPacket* requestFb = GetAndVerify<TRequestPacket>(*request.data());
                        if (!requestFb) {
                            return 400; // Bad Request
                        }

                        auto responseFbBuilder = PSRAMFlatBufferBuilder();

                        int result = service(*requestFb, responseFbBuilder);
                        auto innerData = (result >= 200 && result < 300) ? SerializeFlatBuffer(responseFbBuilder) : psram_vector<uint8_t>{};

                        return OperationResult(result, std::move(innerData));
                    }
                };
        }
    };
}