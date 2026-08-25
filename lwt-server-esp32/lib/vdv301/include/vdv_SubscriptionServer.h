#pragma once

#include "vdv_HttpServerBase.h"
#include <string_view>

namespace vdv301 {

    class SubscriptionServer : public HttpServerBase
    {
    public:
        using ServiceHandler = std::function<void(const std::string_view& operationName, const HttpServerBase::Request& req, HttpServerBase::Response& resp)>;

    public:
        SubscriptionServer(const std::string& ifkey, in_port_t port);

        void RegisterService(const std::string& serviceName, const std::string& path, ServiceHandler handler);
        void UnregisterService(const std::string& serviceName, const std::string& path);

    private:
        static std::string GetServiceUriMatcher(const std::string& serviceName, const std::string& path);
        static std::string_view GetOperationNameFromUri(const std::string_view& uri);
    };
}