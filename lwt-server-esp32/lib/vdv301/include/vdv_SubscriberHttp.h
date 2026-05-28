#pragma once

#include "vdv_SubscriberBase.h"
#include "vdv_ServiceDiscovery.h"
#include <string>
#include <mutex>
#include <vector>
#include <atomic>
#include "EventQueue.h"
#include "esp_http_client.h"
#include "esp_netif_types.h"

namespace vdv301 {

    class SubscriberHttp : public SubscriberBase
    {
    public:
        using OperationIDType = size_t;
    protected:
        class OperationResult {
        private:
            OperationIDType m_OperationID;
            const std::string& m_Result;
        public:
            OperationResult(OperationIDType operationID, const std::string& result);
            template<typename T>
            T GetOperationID() const { return (T)GetRawOperationID(); }
            OperationIDType GetRawOperationID() const;
            const std::string& GetResult() const;
        };
    private:
        ServiceDiscovery& m_SD;
        std::atomic<ServiceDiscovery::BrowseHandle> m_SDHandle{ 0 };
        std::mutex m_CommMutex;
        EventQueue m_EventQueue;

        std::string m_ServiceClassName;

        std::string m_HttpHost;
        std::string m_HttpPathBase;
        std::string m_PublisherInstanceName;
        esp_netif_t* m_PublisherRouteInterface;
        std::string m_ClientIP;
        struct ifreq m_IfaceName;

        esp_http_client_config_t m_BaseHttpConfig{};

        OperationIDType m_SubscribedOperations{ 0 };
        std::vector<std::string> m_SubscribedOperationEndpoints;

    public:
        SubscriberHttp(ServiceDiscovery& sd, const std::string& serviceClassName, const ServiceDiscovery::Query& serviceQuery, OperationIDType subscribedOps);
        ~SubscriberHttp();

        // forbid copy/assignment
        SubscriberHttp(const SubscriberHttp&) = delete;
        SubscriberHttp& operator=(const SubscriberHttp&) = delete;

    private:
        void HandleServiceDiscovered(const ServiceDiscovery::Result& result);
        void HandleServiceLost();

    protected:
        /**
         * @brief Called asynchronously after service discovery has been handled. Implementing class should use this to
         * call SubscribeToOperation() for each operation it wants to subscribe to.
         */
        virtual void OnServiceConnected();
        virtual void OnServiceDisconnected();
        /**
         * @brief Called asynchronously when a publisher has sent data to this subscriber.
         * Implementing class should use the operation name to determine which operation
         * the data belongs to, and then process the data accordingly.
         */
        virtual void OnOperationResult(const OperationResult& result);

        virtual std::string GetOperationName(OperationIDType operation) const = 0;

    protected:
        void SubscribeToOperation(OperationIDType operation);

    private:
        std::string GetOperationPushPath(const std::string& operation) const;
        esp_err_t SendSubscribeRequest(const std::string& operation);

        esp_err_t HandleHttpEvent(esp_http_client_event_t* evt);

        static esp_err_t HttpEventHandlerFunc(esp_http_client_event_t* evt);

        static std::string IPToString(const esp_ip4_addr_t* ip);

        void UpdateServiceStateAsync();
    };
}