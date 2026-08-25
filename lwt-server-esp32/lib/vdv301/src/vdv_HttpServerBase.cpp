#include "vdv_HttpServerBase.h"

#include "esp_netif.h"
#include "esp_log.h"
#include "Overloaded.h"

namespace vdv301 {

    static constexpr const char* TAG = "HttpServer";

    HttpServerBase::HttpServerBase(const std::string& ifkey, in_port_t port) :
        m_IfKey(ifkey),
        m_Port(port)
    {

    }

    HttpServerBase::Request::Request(httpd_req_t* req) :
        m_Req(req)
    {

    }

    httpd_req_t* HttpServerBase::Request::GetRawRequest() const {
        return m_Req;
    }

    const HttpServerBase::Body& HttpServerBase::Request::GetBody() const {
        return m_Body;
    }

    std::string_view HttpServerBase::Request::GetUri() const {
        return std::string_view(m_Req->uri);
    }

    HttpServerBase::Response::Response(httpd_req_t* req) :
        m_Req(req)
    {

    }

    void HttpServerBase::Response::SetStatusCode(int statusCode) {
        m_StatusCode = statusCode;
    }

    void HttpServerBase::Response::SetBody(const Body& body) {
        m_Body = body;
    }

    void HttpServerBase::Response::SetBody(const Body&& body) {
        m_Body = std::move(body);
    }

    void HttpServerBase::Response::SetBodyRef(const Body& body) {
        m_Body = body.c_str();
    }

    void HttpServerBase::Response::SetBodyRef(const std::string& body) {
        m_Body = body.c_str();
    }

    void HttpServerBase::Response::SetBodyRef(const char* body) {
        m_Body = body;
    }

    void HttpServerBase::Response::Send() {
        if (m_Sent) {
            ESP_LOGW(TAG, "Response already sent");
            return;
        }
        m_Sent = true;
        auto statusString = std::to_string(m_StatusCode); // must outlive httpd_resp_send

        httpd_resp_set_status(m_Req, statusString.c_str());

        std::visit(
            overloaded{
                [this](const Body& body) {
                    httpd_resp_send(m_Req, body.c_str(), body.size());
                },
                [this](const char* body) {
                    httpd_resp_send(m_Req, body, HTTPD_RESP_USE_STRLEN);
                },
                [this](std::monostate) {
                    httpd_resp_send(m_Req, "", HTTPD_RESP_USE_STRLEN);
                }
            },
            m_Body
        );
    }

    bool HttpServerBase::Response::IsSent() const {
        return m_Sent;
    }

    void HttpServerBase::Start() {
        std::lock_guard lock(m_Mutex);

        if (m_Httpd) {
            return; // already started
        }

        httpd_config_t config = HTTPD_DEFAULT_CONFIG();
        config.uri_match_fn = httpd_uri_match_wildcard;
        config.server_port = m_Port;
        config.ctrl_port = m_Port + 1;
        ifreq ifr;
        if (!m_IfKey.empty()) {
            auto ethif = esp_netif_get_handle_from_ifkey(m_IfKey.c_str());
            if (ethif) {
                esp_netif_get_netif_impl_name(ethif, ifr.ifr_name);
                config.if_name = &ifr;
            }
            else {
                ESP_LOGE(TAG, "Failed to get network interface for ifkey %s", m_IfKey.c_str());
            }
        }
        ESP_ERROR_CHECK(httpd_start(&m_Httpd, &config));
    }

    void HttpServerBase::Stop() {
        std::lock_guard lock(m_Mutex);

        if (m_Httpd) {
            ESP_ERROR_CHECK(httpd_stop(m_Httpd));
            m_Httpd = nullptr;
        }
    }

    bool HttpServerBase::IsRunning() {
        std::lock_guard lock(m_Mutex);
        return m_Httpd != nullptr;
    }

    in_port_t HttpServerBase::GetPort() const {
        return m_Port;
    }

    HttpServerBase::HandlerContext::HandlerContext(HttpServerBase* server, Handler&& handler) :
        m_Server(server),
        m_Handler(std::move(handler))
    {

    }

    void HttpServerBase::RegisterHandler(const std::string& path, Handler handler) {
        if (!IsRunning()) {
            Start();
        }

        std::lock_guard lock(m_Mutex);

        std::string finalPath = NormalizePath(path);

        HandlerContext* context = new HandlerContext(this, std::move(handler));

        m_Handlers[finalPath] = std::unique_ptr<HandlerContext>(context);

        httpd_uri_t uriHandler{
            .uri = finalPath.c_str(),
            .method = HTTP_POST,
            .handler = HttpRequestHandlerFunc,
            .user_ctx = context,
        };

        esp_err_t err = httpd_register_uri_handler(m_Httpd, &uriHandler);
        if (err == ESP_ERR_HTTPD_HANDLER_EXISTS) {
            httpd_unregister_uri_handler(m_Httpd, finalPath.c_str(), HTTP_POST); // in case it already exists, ignore error
            err = httpd_register_uri_handler(m_Httpd, &uriHandler);
        }
        ESP_ERROR_CHECK(err);
    }

    void HttpServerBase::UnregisterHandler(const std::string& path) {
        std::lock_guard lock(m_Mutex);

        std::string finalPath = NormalizePath(path);
        m_Handlers.erase(finalPath);
        ESP_ERROR_CHECK(httpd_unregister_uri_handler(m_Httpd, finalPath.c_str(), HTTP_POST));
    }

    std::string HttpServerBase::NormalizePath(const std::string& path) {
        if (!path.starts_with("/")) {
            return "/" + path;
        }
        return path;
    }

    esp_err_t HttpServerBase::HandleHttpRequest(httpd_req_t* req, HandlerContext* context) {
        std::lock_guard lock(m_Mutex);

        psram_string body;

        if (req->content_len) {
            body.resize(req->content_len);
            char* bodyPtr = body.data();
            size_t remaining = req->content_len;
            int received;
            while ((received = httpd_req_recv(req, bodyPtr, remaining)) > 0) {
                bodyPtr += received;
                remaining -= received;
            }
        }
        else {
            psram_vector<char> buf(128);
            int received;
            while ((received = httpd_req_recv(req, buf.data(), buf.size())) > 0) {
                body.append(buf.data(), received);
            }
        }

        Request request(req);
        request.m_Body = std::move(body);
        Response response(req);

        context->m_Handler(request, response);

        if (!response.IsSent()) {
            response.Send();
        }

        return ESP_OK;
    }

    esp_err_t HttpServerBase::HttpRequestHandlerFunc(httpd_req_t* req) {
        HandlerContext* context = static_cast<HandlerContext*>(req->user_ctx);
        return context->m_Server->HandleHttpRequest(req, context);
    }
}