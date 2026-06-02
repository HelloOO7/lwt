#include "vdv_HttpPushServer.h"

namespace vdv301 {

    HttpPushServer::HttpPushServer(uint16_t port) : m_Port(port)
    {

    }

    void HttpPushServer::Start() {
        std::lock_guard lock(m_Mutex);
        
        if (m_Httpd) {
            return; // already started
        }

        httpd_config_t config = HTTPD_DEFAULT_CONFIG();
        config.server_port = m_Port;
        ESP_ERROR_CHECK(httpd_start(&m_Httpd, &config));
    }

    void HttpPushServer::Stop() {
        std::lock_guard lock(m_Mutex);

        if (m_Httpd) {
            ESP_ERROR_CHECK(httpd_stop(m_Httpd));
            m_Httpd = nullptr;
        }
    }

    bool HttpPushServer::IsRunning() {
        std::lock_guard lock(m_Mutex);
        return m_Httpd != nullptr;
    }

    void HttpPushServer::RegisterPushEndpoint(const std::string& path, PushConsumer consumer) {
        std::lock_guard lock(m_Mutex);

        std::string finalPath = NormalizePath(path);

        m_PushEndpoints[finalPath] = consumer;

        httpd_uri_t uriHandler{
            .uri = finalPath.c_str(),
            .method = HTTP_POST,
            .handler = HttpRequestHandlerFunc,
            .user_ctx = this,
        };
        esp_err_t err = httpd_register_uri_handler(m_Httpd, &uriHandler);
        if (err == ESP_ERR_HTTPD_HANDLER_EXISTS) {
            httpd_unregister_uri_handler(m_Httpd, finalPath.c_str(), HTTP_POST); // in case it already exists, ignore error
            err = httpd_register_uri_handler(m_Httpd, &uriHandler);
        }
        ESP_ERROR_CHECK(err);
    }

    void HttpPushServer::UnregisterPushEndpoint(const std::string& path) {
        std::lock_guard lock(m_Mutex);

        std::string finalPath = NormalizePath(path);
        m_PushEndpoints.erase(finalPath);
        ESP_ERROR_CHECK(httpd_unregister_uri_handler(m_Httpd, finalPath.c_str(), HTTP_POST));
    }

    std::string HttpPushServer::NormalizePath(const std::string& path) {
        if (!path.starts_with("/")) {
            return "/" + path;
        }
        return path;
    }

    esp_err_t HttpPushServer::HandleHttpRequest(httpd_req_t* req) {
        std::lock_guard lock(m_Mutex);
        
        auto it = m_PushEndpoints.find(req->uri);
        if (it == m_PushEndpoints.end()) {
            return ESP_FAIL; // no handler for this path
        }

        std::string body;
        if (req->content_len) {
            body.reserve(req->content_len);
        }
        psram_vector<char> buf(128);
        int received;
        while ((received = httpd_req_recv(req, buf.data(), buf.size())) > 0) {
            body.append(buf.data(), received);
        }

        it->second(body);

        httpd_resp_send(req, "", HTTPD_RESP_USE_STRLEN);
        return ESP_OK;
    }

    esp_err_t HttpPushServer::HttpRequestHandlerFunc(httpd_req_t* req) {
        HttpPushServer* server = static_cast<HttpPushServer*>(req->user_ctx);
        return server->HandleHttpRequest(req);
    }
}