#pragma once

#include "vdv_ServiceDiscovery.h"
#include "vdv_PublisherBase.h"
#include "PSRAMContainers.h"
#include "esp_http_client.h"
#include <unordered_map>
#include <memory>
#include <optional>
#include "TimerProc.h"

namespace vdv301 {

    class PublisherHttp : public PublisherBase
    {
    public:
        using OperationIDType = uint8_t;

        static constexpr OperationIDType DefineOp(int index) { return index; }

        enum class PublishMode {
            ONESHOT,
            CONTINUOUS
        };

    private:
        enum class OperationClass {
            NONE,
            GET,
            RETRIEVE,
            SUBSCRIBE,
            UNSUBSCRIBE
        };

        struct Subscription {
            psram_string m_SubscriberUri;
            bool m_IsDirty{ true };
        };

        struct PublishState {
            PublisherHttp* m_Parent;
            OperationIDType m_Operation;
            bool m_Enabled{ false };
            psram_vector<Subscription> m_Subscribers;
            psram_string m_Data;
            bool m_IsDataSet{ false };
            PublishMode m_Mode{ PublishMode::CONTINUOUS };
            size_t m_Heartbeat{ 0 };
            std::optional<TimerProc> m_HeartbeatTimer;
            size_t m_RetryCount{ 0 };

            PublishState(PublisherHttp* parent, OperationIDType operation);

            void InitTimer();
            void DeleteTimer();
        };

    private:
        ServiceDiscovery& m_SD;
        ServiceDiscovery::PublishHandle m_SDPublishHandle{ 0 };
        std::string m_ServiceName;
        std::string m_ServicePath;

        std::mutex m_Mutex;
        EventQueue m_EventQueue;

        // must be unique_ptr so that we can use it as context for callbacks
        // (otherwise pointer to vector element may become invalid if vector is resized)
        psram_vector<std::unique_ptr<PublishState>> m_PublishStates;
        std::unordered_map<psram_string, OperationIDType> m_OpNameToID;

    public:
        PublisherHttp(ServiceDiscovery& sd, const std::string& serviceClassName, const std::string& serviceVersion, size_t taskStackSize);
        ~PublisherHttp();

    protected:
        void BindToSubscriptionServer();
        void UnbindFromSubscriptionServer();

        PublishState& GetPublishState(OperationIDType operation);
        void PublishData(OperationIDType operation, psram_string&& data, PublishMode mode, size_t heartbeat = 0);
        void SetOperationEnabled(OperationIDType operation, bool enabled);

        void AddSubscriber(PublishState& state, const psram_string& subscriberUri);
        bool RemoveSubscriber(PublishState& state, const psram_string& subscriberUri);
        void SendDataToSubscribersAsync(OperationIDType operation);
        void SendDataToSubscribers(OperationIDType operation);
        size_t SendDataToSubscriber(OperationIDType operation, const psram_string& subscriberUri, PublishState& state);

        OperationClass GetOperationClass(const std::string_view& operationName) const;
        psram_string GetBaseOperationName(const std::string_view& fullOperationName, OperationClass opClass) const;

    protected:
        virtual std::string GetOperationName(OperationIDType operation) const = 0;
    };
}