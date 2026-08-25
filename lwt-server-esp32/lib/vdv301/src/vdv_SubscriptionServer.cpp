#include "vdv_SubscriptionServer.h"

namespace vdv301 {

    SubscriptionServer::SubscriptionServer(const std::string& ifkey, in_port_t port) :
        HttpServerBase(ifkey, port)
    {

    }

    void SubscriptionServer::RegisterService(const std::string& serviceName, const std::string& path, ServiceHandler handler) {
        RegisterHandler(
            GetServiceUriMatcher(serviceName, path),
            [handler](const Request& req, Response& resp) {
                handler(GetOperationNameFromUri(req.GetUri()), req, resp);
            }
        );
    }

    void SubscriptionServer::UnregisterService(const std::string& serviceName, const std::string& path) {
        UnregisterHandler(GetServiceUriMatcher(serviceName, path));
    }

    std::string SubscriptionServer::GetServiceUriMatcher(const std::string& serviceName, const std::string& path) {
        std::string res = "/" + serviceName + "/*";
        if (!path.empty()) {
            res = path + res;
            if (!path.starts_with('/')) {
                res = "/" + res;
            }
        }
        return res;
    }

    std::string_view SubscriptionServer::GetOperationNameFromUri(const std::string_view& uri) {
        auto lastSlashPos = uri.find_last_of('/');
        if (lastSlashPos == std::string_view::npos || lastSlashPos + 1 >= uri.size()) {
            return {};
        }
        return uri.substr(lastSlashPos + 1);
    }
}