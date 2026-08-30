#pragma once

#include "vdv_ServiceDiscovery.h"
#include <atomic>
#include <mutex>

namespace vdv301 {

    class SubscriberBase
    {
    private:
        ServiceDiscovery& m_SD;
        std::atomic<ServiceDiscovery::BrowseHandle> m_SDHandle{ 0 };
    protected:
        std::mutex m_CommMutex;
    protected:
        SubscriberBase(ServiceDiscovery& sd);
    public:
        virtual ~SubscriberBase();

    protected:
        /**
         * @brief Start browsing for services matching the query. This must be called in the
         * derived class constructor, and nowhere else.
         * 
         * @param serviceQuery 
         */
        void StartBrowse(const ServiceDiscovery::Query& serviceQuery);
        /**
         * @brief Stop browsing for services. This must be called in the derived class destructor, and nowhere else.
         */
        void StopBrowse();

        virtual void HandleServiceDiscovered(const ServiceDiscovery::Result& result) = 0;
        virtual void HandleServiceLost() = 0;
    };
}