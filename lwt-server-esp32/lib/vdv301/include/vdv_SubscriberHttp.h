#pragma once

#include "vdv_SubscriberBase.h"
#include "vdv_ServiceDiscovery.h"
#include <string>
#include <mutex>
#include <vector>
#include <atomic>
#include "esp_http_client.h"
#include "esp_netif_types.h"

namespace vdv301 {

    class SubscriberHttp : public SubscriberBase
    {
    private:
        ServiceDiscovery& m_SD;
        std::atomic<ServiceDiscovery::BrowseHandle> m_SDHandle{ 0 };
        std::mutex m_CommMutex;

        std::string m_ServiceClassName;

        std::string m_HttpHost;
        std::string m_HttpPathBase;
        std::string m_PublisherInstanceName;
        esp_netif_t* m_PublisherRouteInterface;
        std::string m_ClientIP;
        struct ifreq m_IfaceName;

        esp_http_client_config_t m_BaseHttpConfig{};

        std::vector<std::string> m_SubscribedOperationEndpoints;

    public:
        SubscriberHttp(ServiceDiscovery& sd, const std::string& serviceClassName, const ServiceDiscovery::Query& serviceQuery);
        ~SubscriberHttp();

        // forbid copy/assignment
        SubscriberHttp(const SubscriberHttp&) = delete;
        SubscriberHttp& operator=(const SubscriberHttp&) = delete;

    protected:
        virtual void OnServiceDiscovered(const ServiceDiscovery::Result& result);
        virtual void OnServiceLost();

        /**
         * @brief Called upon service discovery. Implementing class should use this to
         * call SubscribeToOperation() for each operation it wants to subscribe to.
         */
        virtual void OnSubscribe();
        /**
         * @brief Called asynchronously when a publisher has sent data to this subscriber.
         * Implementing class should use the operation name to determine which operation
         * the data belongs to, and then process the data accordingly.
         *
         * @param operation
         * @param result
         */
        virtual void OnOperationResult(const std::string& operation, const std::string& result);

    protected:
        void SubscribeToOperation(const std::string& operation);

    private:
        std::string GetOperationPushPath(const std::string& operation) const;
        void SendSubscribeRequest(const std::string& operation);

        esp_err_t HandleHttpEvent(esp_http_client_event_t* evt);

        static esp_err_t HttpEventHandlerFunc(esp_http_client_event_t* evt);

        static std::string IPToString(const esp_ip4_addr_t* ip);
    };
}