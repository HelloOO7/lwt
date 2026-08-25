#pragma once

#include <cstdint>
#include <functional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <mutex>
#include <memory>
#include <sys/types.h>
#include "esp_http_server.h"
#include "PSRAMContainers.h"
#include <variant>

namespace vdv301 {

    class HttpServerBase
    {
    public:
        using Body = psram_string;

        class Request {
            friend class HttpServerBase;
        private:
            httpd_req_t* m_Req;
            Body m_Body;
        private:
            Request(httpd_req_t* req);
        public:
            httpd_req_t* GetRawRequest() const;
            std::string_view GetUri() const;
            const Body& GetBody() const;
        };

        class Response {
            friend class HttpServerBase;
        private:
            httpd_req_t* m_Req;
            int m_StatusCode{ 200 };
            std::variant<Body, const char*, std::monostate> m_Body{ std::monostate{} };
            bool m_Sent{ false };
        private:
            Response(httpd_req_t* req);
        public:
            void SetStatusCode(int statusCode);
            void SetBody(const Body& body);
            void SetBody(const Body&& body);
            void SetBodyRef(const Body& body);
            void SetBodyRef(const std::string& body);
            void SetBodyRef(const char* body);

            void Send();
            bool IsSent() const;
        };

        using Handler = std::function<void(const Request& req, Response& resp)>;

    private:
        struct HandlerContext {
            HttpServerBase* m_Server;
            Handler m_Handler;

            HandlerContext(HttpServerBase* server, Handler&& handler);
        };

    private:
        std::string m_IfKey;
        in_port_t m_Port;
        httpd_handle_t m_Httpd{ nullptr };
        std::mutex m_Mutex;

        std::unordered_map<std::string, std::unique_ptr<HandlerContext>> m_Handlers;

    public:
        HttpServerBase(const std::string& ifkey, in_port_t port);

        void Start();
        void Stop();
        bool IsRunning();
        in_port_t GetPort() const;

        void RegisterHandler(const std::string& path, Handler handler);
        void UnregisterHandler(const std::string& path);

    private:
        esp_err_t HandleHttpRequest(httpd_req_t* req, HandlerContext* context);
        static esp_err_t HttpRequestHandlerFunc(httpd_req_t* req);

        static std::string NormalizePath(const std::string& path);
    };
}