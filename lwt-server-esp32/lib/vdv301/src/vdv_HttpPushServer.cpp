#include "vdv_HttpPushServer.h"

#include "esp_netif.h"
#include "esp_log.h"

namespace vdv301 {

    static constexpr const char* TAG = "HttpPushServer";

    HttpPushServer::HttpPushServer(const std::string& ifkey, in_port_t port) :
        HttpServerBase(ifkey, port)
    {

    }

    void HttpPushServer::RegisterPushEndpoint(const std::string& path, PushConsumer consumer) {
        RegisterHandler(
            path,
            [consumer](const Request& req, Response& resp) {
                consumer(req.GetBody());
            }
        );
    }

    void HttpPushServer::UnregisterPushEndpoint(const std::string& path) {
        UnregisterHandler(path);
    }
}