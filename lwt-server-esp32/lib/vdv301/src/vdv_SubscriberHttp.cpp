#include "vdv_SubscriberHttp.h"
#include <cstring>
#include <sstream>
#include "vdv_HttpPushServer.h"
#include "esp_log.h"

namespace vdv301
{
    static constexpr const char* TAG = "SubscriberHttp";

    static constexpr uint16_t SUBSCRIBER_PUSH_SERVER_PORT = 31415;

    HttpPushServer g_SubcriberPushServer(SUBSCRIBER_PUSH_SERVER_PORT);

    static constexpr EventQueue::EventTag SERVICE_UPDATE_EVENT_TAG = 1;

    SubscriberHttp::SubscriberHttp(ServiceDiscovery& sd, const std::string& serviceClassName, const ServiceDiscovery::Query& serviceQuery, OperationIDType subscribedOps) :
        SubscriberBase(),
        m_SD{ sd },
        m_EventQueue("SubscriberHttp" + serviceClassName, 5, 6144),
        m_ServiceClassName{ serviceClassName },
        m_SubscribedOperations{ subscribedOps }
    {
        memset(&m_BaseHttpConfig, 0, sizeof(m_BaseHttpConfig));
        std::lock_guard lock(m_CommMutex);

        m_SDHandle = m_SD.StartBrowse(
            serviceQuery,
            [this](const ServiceDiscovery::ResultSetAccessor& results) {
                const ServiceDiscovery::Result* result = results.GetAnyResult();
                if (result) {
                    ESP_LOGI(TAG, "Service query matched: instance=%s host=%s port=%u", result->GetInstanceName().c_str(), result->GetHostName().c_str(), result->GetPort());
                    HandleServiceDiscovered(*result);
                }
                else {
                    ESP_LOGI(TAG, "Service query lost");
                    HandleServiceLost();
                }
            }
        );
    }

    SubscriberHttp::~SubscriberHttp()
    {
        // must not lock here, otherwise we would deadlock
        // (subscriber mutex -> SD mutex vs. SD mutex -> subscriber mutex in HandleServiceDiscovered)
        // fortunately we do not need to lock, as destructor/constructor can not be called concurrently
        auto sdh = m_SDHandle.exchange(0);
        m_SD.StopBrowse(sdh);

        {
            // lock again, so that any pending callbacks before StopBrowse was invoked
            // can finish without accessing a destroyed object
            std::lock_guard lock(m_CommMutex);

            for (auto&& op : m_SubscribedOperationEndpoints) {
                g_SubcriberPushServer.UnregisterPushEndpoint(op);
            }
        }
    }

    void SubscriberHttp::HandleServiceDiscovered(const ServiceDiscovery::Result& result)
    {
        std::lock_guard lock(m_CommMutex);
        auto sdh = m_SDHandle.load();
        if (!sdh) {
            // called after StopBrowse
            return;
        }

        auto* ipv4Addr = result.GetIPv4Address();

        m_HttpHost = ipv4Addr ? IPToString(ipv4Addr) : result.GetHostName();
        m_HttpPathBase = "/";
        auto pathStart = result.GetTxtRecord("path");
        if (pathStart) {
            m_HttpPathBase += *pathStart;
        }
        m_HttpPathBase += m_ServiceClassName + "/";
        m_PublisherInstanceName = result.GetInstanceName();
        m_PublisherRouteInterface = result.GetInterface();

        esp_netif_ip_info_t ipInfo;
        ESP_ERROR_CHECK(esp_netif_get_ip_info(m_PublisherRouteInterface, &ipInfo));
        m_ClientIP = IPToString(&ipInfo.ip);

        m_BaseHttpConfig.host = m_HttpHost.c_str();
        m_BaseHttpConfig.port = result.GetPort();
        ESP_ERROR_CHECK(esp_netif_get_netif_impl_name(m_PublisherRouteInterface, m_IfaceName.ifr_name));
        m_BaseHttpConfig.if_name = &m_IfaceName;

        m_BaseHttpConfig.event_handler = HttpEventHandlerFunc;
        m_BaseHttpConfig.user_data = this;

        m_BaseHttpConfig.user_agent = "VDV301-SubscriberHttp/1.0";
        m_BaseHttpConfig.timeout_ms = 10000;

        UpdateServiceStateAsync();
    }

    void SubscriberHttp::HandleServiceLost()
    {
        m_PublisherInstanceName = "";

        UpdateServiceStateAsync();
    }

    void SubscriberHttp::OnServiceConnected()
    {
        // iterate all operation bits
        for (OperationIDType op = 1; op != 0; op <<= 1) {
            if (op > m_SubscribedOperations) {
                break;
            }
            if ((m_SubscribedOperations & op) != 0) {
                SubscribeToOperation(op);
            }
        }
    }

    void SubscriberHttp::OnServiceDisconnected()
    {

    }

    void SubscriberHttp::OnOperationResult(const OperationResult& result)
    {

    }

    void SubscriberHttp::SubscribeToOperation(OperationIDType operation)
    {
        auto&& operationName = GetOperationName(operation);

        std::string endpointPath = GetOperationPushPath(operationName);

        if (!g_SubcriberPushServer.IsRunning()) {
            g_SubcriberPushServer.Start();
        }

        g_SubcriberPushServer.RegisterPushEndpoint(
            endpointPath,
            [=, this](const HttpPushServer::PushBody& body) {
                if (body.empty()) {
                    ESP_LOGW(TAG, "Push endpoint %s received empty body", operationName.c_str());
                    return;
                }
                m_EventQueue.Post(
                    [=, this]() {
                        ESP_LOGI(TAG, "Received push for operation %s", operationName.c_str());
                        std::lock_guard lock(m_CommMutex);
                        OnOperationResult(OperationResult(operation, body));
                    }
                );
            }
        );
        m_SubscribedOperationEndpoints.push_back(endpointPath);

        SendSubscribeRequest(operationName);
    }

    std::string SubscriberHttp::GetOperationPushPath(const std::string& operation) const
    {
        return "/" + m_PublisherInstanceName + "/" + operation;
    }

    esp_err_t SubscriberHttp::SendSubscribeRequest(const std::string& operation)
    {
        if (!operation.starts_with("Get")) {
            ESP_LOGE(TAG, "Operation name %s does not start with 'Get' - can not subscribe", operation.c_str());
            return ESP_FAIL;
        }

        // translate "GetXyz" to "SubscribeXyz"
        std::string subscribePath = m_HttpPathBase + "Subscribe" + operation.substr(std::string("Get").length());
        m_BaseHttpConfig.path = subscribePath.c_str();

        esp_http_client_handle_t client = esp_http_client_init(&m_BaseHttpConfig);
        ESP_ERROR_CHECK(client ? ESP_OK : ESP_FAIL);

        ESP_ERROR_CHECK(esp_http_client_set_method(client, HTTP_METHOD_POST));
        ESP_ERROR_CHECK(esp_http_client_set_header(client, "Content-Type", "text/xml"));

        std::ostringstream body;
        body << R"(<?xml version="1.0" encoding="UTF-8"?>)";
        body << "<SubscribeRequest>";
        body << "<Client-IP-Address><Value>" << m_ClientIP << "</Value></Client-IP-Address>";
        body << "<ReplyPort><Value>" << SUBSCRIBER_PUSH_SERVER_PORT << "</Value></ReplyPort>";
        body << "<ReplyPath><Value>" << GetOperationPushPath(operation) << "</Value></ReplyPath>";
        body << "</SubscribeRequest>";

        char url[128];
        esp_http_client_get_url(client, url, sizeof(url));

        auto bodyStr = body.str();
        ESP_LOGI(TAG, "POST Subscribe %s\n%s", url, bodyStr.c_str());

        ESP_ERROR_CHECK(esp_http_client_set_post_field(client, bodyStr.c_str(), bodyStr.size()));

        esp_err_t err = esp_http_client_perform(client);

        ESP_ERROR_CHECK(esp_http_client_cleanup(client));

        if (err == ESP_OK) {
            ESP_LOGI(TAG, "Subscribe request sent successfully");
        }
        else {
            ESP_LOGE(TAG, "Failed to send subscribe request: %s", esp_err_to_name(err));
        }

        return err;
    }

    esp_err_t SubscriberHttp::HandleHttpEvent(esp_http_client_event_t* evt)
    {
        // if we need to lock m_CommMutex here, ONLY do it if is_async is true on HTTP config,
        // otherwise we are waiting on esp_http_client_perform() and we would deadlock.

        ESP_LOGI(TAG, "HTTP event %d", evt->event_id);

        return ESP_OK;
    }

    esp_err_t SubscriberHttp::HttpEventHandlerFunc(esp_http_client_event_t* evt)
    {
        if (evt->user_data) {
            SubscriberHttp* subscriber = static_cast<SubscriberHttp*>(evt->user_data);
            return subscriber->HandleHttpEvent(evt);
        }
        return ESP_OK;
    }

    std::string SubscriberHttp::IPToString(const esp_ip4_addr_t* ip) {
        char ipStr[16];
        snprintf(ipStr, sizeof(ipStr), IPSTR, IP2STR(ip));
        return std::string(ipStr);
    }

    void SubscriberHttp::UpdateServiceStateAsync()
    {
        m_EventQueue.Post([this]() {
            std::lock_guard lock(m_CommMutex);
            if (!m_PublisherInstanceName.empty()) {
                OnServiceConnected();
            }
            else {
                OnServiceDisconnected();
            }
            }, SERVICE_UPDATE_EVENT_TAG);
    }

    SubscriberHttp::OperationResult::OperationResult(OperationIDType operationID, const HttpPushServer::PushBody& result) :
        m_OperationID{ operationID },
        m_Result{ result }
    {

    }

    SubscriberHttp::OperationIDType SubscriberHttp::OperationResult::GetRawOperationID() const
    {
        return m_OperationID;
    }

    const HttpPushServer::PushBody& SubscriberHttp::OperationResult::GetResult() const
    {
        return m_Result;
    }
}