#pragma once

#include <cstdint>
#include <functional>
#include <string>
#include <unordered_map>
#include <mutex>
#include "esp_http_server.h"
#include "PSRAMContainers.h"

namespace vdv301 {

    class HttpPushServer
    {
    public:
        using PushBody = psram_string;
        using PushConsumer = std::function<void(const PushBody& body)>;

    private:
        std::string m_IfKey;
        uint16_t m_Port;
        httpd_handle_t m_Httpd { nullptr };
        std::mutex m_Mutex;

        std::unordered_map<std::string, PushConsumer> m_PushEndpoints;

    public:
        HttpPushServer(const std::string& ifkey, uint16_t port);

        void Start();
        void Stop();
        bool IsRunning();

        void RegisterPushEndpoint(const std::string& path, PushConsumer consumer);
        void UnregisterPushEndpoint(const std::string& path);

    private:
        esp_err_t HandleHttpRequest(httpd_req_t* req);
        static esp_err_t HttpRequestHandlerFunc(httpd_req_t* req);

        static std::string NormalizePath(const std::string& path);
    };
}