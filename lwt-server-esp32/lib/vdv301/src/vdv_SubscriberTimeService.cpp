#include "vdv_SubscriberTimeService.h"

namespace vdv301 {

    TimeConfiguration::TimeConfiguration()
    {
    }

    const std::string& TimeConfiguration::GetSntpServer() const
    {
        return m_SntpServer;
    }

    uint16_t TimeConfiguration::GetSntpPort() const
    {
        return m_SntpPort;
    }

    const std::string& TimeConfiguration::GetProlepticTZ() const
    {
        return m_ProlepticTZ;
    }

    SubscriberTimeService::SubscriberTimeService(ServiceDiscovery& sd, int taskPriority) :
        SubscriberBase(sd)
    {
        StartBrowse(
            ServiceDiscovery::QueryBuilder()
            .FilterInstanceName("TimeService*")
            .Build()
        );
    }

    SubscriberTimeService::~SubscriberTimeService()
    {
        StopBrowse();
    }

    void SubscriberTimeService::ObserveTimeConfiguration(Observer<TimeConfiguration>& observer)
    {
        AddObserver(observer);
    }

    void SubscriberTimeService::RemoveObserver(Observer<TimeConfiguration>& observer)
    {
        Observable<TimeConfiguration>::RemoveObserver(observer);
    }

    void SubscriberTimeService::HandleServiceDiscovered(const ServiceDiscovery::Result& result)
    {
        TimeConfiguration config;
        auto sntpServer = result.GetTxtRecord("sntp-server");
        if (sntpServer) {
            config.m_SntpServer = *sntpServer;
        }
        else {
            config.m_SntpServer = result.GetIPv4AddressAsString().value_or(result.GetHostName());
            config.m_SntpPort = result.GetPort();
        }
        auto timezone = result.GetTxtRecord("timezone");
        if (timezone) {
            config.m_ProlepticTZ = IbisIpToProleptic(*timezone);
        }
        else {
            config.m_ProlepticTZ = "UTC";
        }
        NotifyObservers(&config);
    }

    void SubscriberTimeService::HandleServiceLost()
    {
        InvalidateObservers();
    }

    std::string SubscriberTimeService::IbisIpToProleptic(const std::string& ibisIpTZ) const
    {
        // IBIS-IP is in format UTC+-h:mm, proleptic posix TZ is the same, but with the sign inverted

        std::string result;
        std::transform(ibisIpTZ.begin(), ibisIpTZ.end(), std::back_inserter(result),
            [](char c) {
                switch (c) {
                case '+':
                    return '-';
                case '-':
                    return '+';
                default:
                    return c;
                }
            }
        );

        return result;
    }
}