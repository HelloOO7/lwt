#pragma once

#include "vdv_SubscriberBase.h"
#include "Observable.h"

namespace vdv301 {

    class SubscriberTimeService;

    class TimeConfiguration {
        friend class SubscriberTimeService;
    private:
        std::string m_SntpServer;
        uint16_t m_SntpPort{ 123 };
        std::string m_ProlepticTZ;

    private:
        TimeConfiguration();

    public:
        const std::string& GetSntpServer() const;
        uint16_t GetSntpPort() const;
        const std::string& GetProlepticTZ() const;
    };

    class SubscriberTimeService : public SubscriberBase, public Observable<TimeConfiguration>
    {
    public:
        SubscriberTimeService(ServiceDiscovery& sd, int taskPriority = EventQueue::DEFAULT_TASK_PRIORITY);
        ~SubscriberTimeService();

        void ObserveTimeConfiguration(Observer<TimeConfiguration>& observer);
        void RemoveObserver(Observer<TimeConfiguration>& observer);

    protected:
        virtual void HandleServiceDiscovered(const ServiceDiscovery::Result& result) override;
        virtual void HandleServiceLost() override;

    private:
        std::string IbisIpToProleptic(const std::string& ibisIpTZ) const;
    };
}