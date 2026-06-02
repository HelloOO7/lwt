#include "lwt_PingService.h"

#include "operations_generated.h"
#include "ping_generated.h"
#include "esp_timer.h"
#include "esp_netif.h"

namespace lwt {

    void PingService::Register(ServiceRegistry& registry) {
        registry.RegisterServiceCallback(Operation_Ping, ApplicationServer::CreateOperationServiceFunc<void>(
            [](flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                const char* hostname = "<unknown>";
                esp_netif_get_hostname(esp_netif_get_default_netif(), &hostname);
                auto respOff = CreatePingResponseDirect(fbb, hostname, esp_timer_get_time());
                fbb.Finish(respOff);
                return 200;
            }
        ));
    }
}