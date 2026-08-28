#include "vdv_PublisherHttp.h"
#include "vdv_SubscriptionServer.h"
#include "esp_log.h"
#include "IBIS_IP_common_V2_3CZ1_0.hpp"
#include "NewAndDelete.h"

namespace vdv301 {

    static constexpr const char* TAG = "PublisherHttp";

    static constexpr uint16_t SUBSCRIPTION_SERVER_PORT = 31425;

    SubscriptionServer g_SubscriptionServer("ETH_DEF", SUBSCRIPTION_SERVER_PORT);

    static size_t MAX_FAILURES_FOR_DISCONNECT = 3;

    using namespace IBIS_IP_common_V2_3CZ1_0;

    PublisherHttp::PublisherHttp(ServiceDiscovery& sd, const std::string& serviceClassName, const std::string& serviceVersion, size_t taskStackSize) :
        PublisherBase(),
        m_SD{ sd },
        m_ServiceName{ serviceClassName },
        m_ServicePath{ serviceVersion },
        m_EventQueue(serviceClassName, 5, taskStackSize)
    {
        std::lock_guard lock(m_Mutex);

        m_SDPublishHandle = m_SD.PublishService(
            ServiceDiscovery::ServiceInfoBuilder()
            .SetInstanceName(serviceClassName + "_" + serviceVersion)
            .SetPort(SUBSCRIPTION_SERVER_PORT)
            .AddTxtRecord("ver", serviceVersion)
            .AddTxtRecord("path", m_ServicePath)
            .Build()
        );

        BindToSubscriptionServer();
    }

    PublisherHttp::~PublisherHttp()
    {
        std::lock_guard lock(m_Mutex);

        m_SD.StopPublish(m_SDPublishHandle);
        UnbindFromSubscriptionServer();

        for (auto&& state : m_PublishStates) {
            state->DeleteTimer();
        }
    }

    template<typename TRequestStructure>
        requires std::is_same_v<TRequestStructure, SubscribeRequestStructure> || std::is_same_v<TRequestStructure, UnsubscribeRequestStructure>
    psram_string CreateSubscriberUri(const TRequestStructure& request) {
        psram_string uri = "http://";
        uri += request.Client_IP_Address.Value;
        if (request.ReplyPort) {
            uri += ":";
            uri += std::to_string(request.ReplyPort->Value);
        }
        if (request.ReplyPath) {
            uri += "/";
            uri += request.ReplyPath->Value;
        }
        return uri;
    }

    void PublisherHttp::BindToSubscriptionServer() {
        g_SubscriptionServer.RegisterService(
            m_ServiceName,
            m_ServicePath,
            [this](const std::string_view& operationName, const HttpServerBase::Request& req, HttpServerBase::Response& resp) {
                std::lock_guard lock(m_Mutex);

                auto opClass = GetOperationClass(operationName);
                auto baseOpName = GetBaseOperationName(operationName, opClass);

                auto it = m_OpNameToID.find(baseOpName);
                if (it == m_OpNameToID.end() || opClass == OperationClass::NONE) {
                    ESP_LOGE(TAG, "Received request for unknown operation: %.*s", (int)operationName.size(), operationName.data());
                    resp.SetStatusCode(404);
                    return;
                }

                try {
                    auto&& publishState = GetPublishState(it->second);
                    if (opClass == OperationClass::GET || opClass == OperationClass::RETRIEVE) {
                        resp.SetBodyRef(publishState.m_Data);
                        resp.Send(); // send so that ref is still valid
                    }
                    else if (opClass == OperationClass::SUBSCRIBE) {
                        SubscribeRequestStructure request;
                        {
                            UseHeapCaps<MALLOC_CAP_SPIRAM> usePsram;
                            load_data(req.GetBody().c_str(), request);
                        }

                        auto subscriberUri = CreateSubscriberUri(request);
                        AddSubscriber(publishState, subscriberUri);
                        if (publishState.m_Mode == PublishMode::CONTINUOUS && publishState.m_Enabled && publishState.m_IsDataSet) {
                            // if continuous, request re-send to dirty peers (this subscriber)
                            SendDataToSubscribersAsync(it->second);
                        }

                        std::string responseBody;

                        {
                            UseHeapCaps<MALLOC_CAP_SPIRAM> usePsram;

                            SubscribeResponseStructure response;
                            response.Active = IBIS_IP_boolean{ true };
                            if (publishState.m_Heartbeat) {
                                response.Heartbeat = IBIS_IP_duration{ IBIS_IP_duration_Value_t{ "P" + std::to_string(publishState.m_Heartbeat) + "S" } };
                            }

                            responseBody = save_data(response);
                        }

                        resp.SetBodyRef(responseBody);
                        resp.Send();
                    }
                    else if (opClass == OperationClass::UNSUBSCRIBE) {
                        psram_string subscriberUri;
                        {
                            UseHeapCaps<MALLOC_CAP_SPIRAM> usePsram;
                            UnsubscribeRequestStructure request;
                            load_data(req.GetBody().c_str(), request);
                            subscriberUri = CreateSubscriberUri(request);
                        }
                        bool removed = RemoveSubscriber(publishState, subscriberUri);

                        std::string responseBody;

                        {
                            UseHeapCaps<MALLOC_CAP_SPIRAM> usePsram;

                            UnsubscribeResponseStructure response;
                            response.Active = IBIS_IP_boolean{ false };
                            if (!removed) {
                                resp.SetStatusCode(404);
                                response.OperationErrorMessage = IBIS_IP_string{ "Subscriber not found" };
                            }

                            responseBody = save_data(response);
                        }

                        resp.SetBodyRef(responseBody);
                        resp.Send();
                    }
                }
                catch (const std::exception& e) {
                    ESP_LOGE(TAG, "Exception while handling request for operation %.*s: %s", (int)operationName.size(), operationName.data(), e.what());
                    resp.SetStatusCode(500);
                }
            });
    }

    void PublisherHttp::UnbindFromSubscriptionServer() {
        g_SubscriptionServer.UnregisterService(m_ServiceName, m_ServicePath);
    }

    PublisherHttp::PublishState::PublishState(PublisherHttp* parent, OperationIDType operation) :
        m_Parent(parent),
        m_Operation(operation)
    {
    }

    void PublisherHttp::PublishState::InitTimer() {
        if (!m_HeartbeatTimer) {
            m_HeartbeatTimer.emplace(
                [this]()
                {
                    for (auto&& sub : m_Subscribers) {
                        sub.m_IsDirty = true; // force send to all subscribers on heartbeat
                    }
                    m_Parent->SendDataToSubscribersAsync(m_Operation);
                },
                (uint64_t)m_Heartbeat * 1000 * 1000, // convert seconds to microseconds
                TimerProc::Type::PERIODIC
            );
            m_HeartbeatTimer->Start();
        }
    }

    void PublisherHttp::PublishState::DeleteTimer() {
        if (m_HeartbeatTimer) {
            m_HeartbeatTimer->Stop();
            m_HeartbeatTimer.reset();
        }
    }

    PublisherHttp::PublishState& PublisherHttp::GetPublishState(OperationIDType operation) {
        while (m_PublishStates.size() <= operation) {
            m_OpNameToID[psram_string(GetOperationName(m_PublishStates.size()))] = m_PublishStates.size();
            m_PublishStates.emplace_back(new (MALLOC_CAP_SPIRAM) PublishState(this, m_PublishStates.size()));
        }
        return *m_PublishStates[operation];
    }

    void PublisherHttp::PublishData(OperationIDType operation, psram_string&& data, PublishMode mode, size_t heartbeat) {
        std::lock_guard lock(m_Mutex);

        auto&& publishState = GetPublishState(operation);
        publishState.m_Enabled = true;
        publishState.m_Data = std::move(data);
        publishState.m_IsDataSet = true;
        publishState.m_Mode = mode;
        publishState.m_Heartbeat = heartbeat;

        for (auto&& sub : publishState.m_Subscribers) {
            sub.m_IsDirty = true;
        }

        if (publishState.m_Heartbeat) {
            publishState.InitTimer();
        }
        else {
            publishState.DeleteTimer();
        }

        SendDataToSubscribersAsync(operation);
    }

    void PublisherHttp::SetOperationEnabled(OperationIDType operation, bool enabled) {
        std::lock_guard lock(m_Mutex);

        auto&& publishState = GetPublishState(operation);
        publishState.m_Enabled = enabled;
        if (!enabled) {
            if (publishState.m_HeartbeatTimer) {
                publishState.DeleteTimer();
            }
        }
        else {
            if (publishState.m_Mode == PublishMode::CONTINUOUS && publishState.m_IsDataSet) {
                SendDataToSubscribersAsync(operation);
            }
        }
    }

    void PublisherHttp::SendDataToSubscribersAsync(OperationIDType operation) {
        m_EventQueue.Post(
            [this, operation]() {
                SendDataToSubscribers(operation);
            },
            operation // use operation as tag
        );
    }

    void PublisherHttp::SendDataToSubscribers(OperationIDType operation) {
        std::lock_guard lock(m_Mutex);

        auto& state = GetPublishState(operation);
        if (!state.m_Enabled || !state.m_IsDataSet) {
            return;
        }

        for (auto&& sub : state.m_Subscribers) {
            if (!sub.m_IsDirty) {
                continue;
            }
            if (SendDataToSubscriber(operation, sub.m_SubscriberUri, state) > MAX_FAILURES_FOR_DISCONNECT) {
                ESP_LOGW(TAG, "Subscriber %s exceeded max failures, removing from list", sub.m_SubscriberUri.c_str());
                RemoveSubscriber(state, sub.m_SubscriberUri);
            }
        }

        if (state.m_HeartbeatTimer) {
            state.m_HeartbeatTimer->Restart();
        }
    }

    void PublisherHttp::AddSubscriber(PublishState& state, const psram_string& subscriberUri) {
        auto existingSub = std::find_if(state.m_Subscribers.begin(), state.m_Subscribers.end(), [&](const Subscription& sub) { return sub.m_SubscriberUri == subscriberUri; });
        if (existingSub == state.m_Subscribers.end()) {
            state.m_Subscribers.push_back(Subscription{ subscriberUri, state.m_Mode == PublishMode::CONTINUOUS });
            ESP_LOGI(TAG, "Service %s: Added subscriber: %s", m_ServiceName.c_str(), subscriberUri.c_str());
        }
    }

    bool PublisherHttp::RemoveSubscriber(PublishState& state, const psram_string& subscriberUri) {
        bool erased = std::erase_if(state.m_Subscribers, [&](const Subscription& sub) { return sub.m_SubscriberUri == subscriberUri; }) > 0;
        if (erased) {
            ESP_LOGI(TAG, "Service %s: Removed subscriber: %s", m_ServiceName.c_str(), subscriberUri.c_str());
        }
        else {
            ESP_LOGW(TAG, "Service %s: Attempted to remove non-existent subscriber: %s", m_ServiceName.c_str(), subscriberUri.c_str());
        }
        return erased;
    }

    size_t PublisherHttp::SendDataToSubscriber(OperationIDType operation, const psram_string& subscriberUri, PublishState& state) {
        esp_http_client_config_t config{};
        config.url = subscriberUri.c_str();
        config.method = HTTP_METHOD_POST;
        config.user_agent = "VDV301_PublisherHttp";
        esp_http_client_handle_t client = esp_http_client_init(&config);
        assert(client != nullptr);

        ESP_ERROR_CHECK(esp_http_client_set_header(client, "Content-Type", "text/xml; charset=utf-8"));
        ESP_ERROR_CHECK(esp_http_client_set_header(client, "Content-Length", std::to_string(state.m_Data.size()).c_str()));
        ESP_ERROR_CHECK(esp_http_client_set_post_field(client, state.m_Data.c_str(), state.m_Data.size()));

        esp_err_t err = esp_http_client_perform(client);

        if (err == ESP_OK && esp_http_client_get_status_code(client) == 200) {
            ESP_LOGI(TAG, "Data sent to subscriber %s successfully", subscriberUri.c_str());
            state.m_RetryCount = 0;
        }
        else {
            ESP_LOGE(TAG, "Failed to send data to subscriber %s: %s", subscriberUri.c_str(), esp_err_to_name(err));
            state.m_RetryCount++;
        }

        ESP_ERROR_CHECK(esp_http_client_cleanup(client));

        return state.m_RetryCount;
    }

    PublisherHttp::OperationClass PublisherHttp::GetOperationClass(const std::string_view& operationName) const {
        if (operationName.starts_with("Get")) {
            return OperationClass::GET;
        }
        else if (operationName.starts_with("Retrieve")) {
            return OperationClass::RETRIEVE;
        }
        else if (operationName.starts_with("Subscribe")) {
            return OperationClass::SUBSCRIBE;
        }
        else if (operationName.starts_with("Unsubscribe")) {
            return OperationClass::UNSUBSCRIBE;
        }
        else {
            return OperationClass::NONE;
        }
    }

    psram_string PublisherHttp::GetBaseOperationName(const std::string_view& fullOperationName, OperationClass opClass) const {
        switch (opClass) {
        case OperationClass::GET:
            return psram_string(fullOperationName.substr(3)); // remove "Get" prefix
        case OperationClass::RETRIEVE:
            return psram_string(fullOperationName.substr(8)); // remove "Retrieve" prefix
        case OperationClass::SUBSCRIBE:
            return psram_string(fullOperationName.substr(9)); // remove "Subscribe" prefix
        case OperationClass::UNSUBSCRIBE:
            return psram_string(fullOperationName.substr(11)); // remove "Unsubscribe" prefix
        default:
            return psram_string(fullOperationName);
        };
    }
}