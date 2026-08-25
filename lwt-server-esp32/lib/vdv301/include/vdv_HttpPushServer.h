#pragma once

#include "vdv_HttpServerBase.h"

namespace vdv301 {

    class HttpPushServer : public HttpServerBase
    {
    public:
        using PushBody = HttpServerBase::Body;
        using PushConsumer = std::function<void(const PushBody& body)>;

    public:
        HttpPushServer(const std::string& ifkey, in_port_t port);

        void RegisterPushEndpoint(const std::string& path, PushConsumer consumer);
        void UnregisterPushEndpoint(const std::string& path);
    };
}