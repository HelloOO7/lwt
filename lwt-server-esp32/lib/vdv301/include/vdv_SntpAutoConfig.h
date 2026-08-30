#pragma once

#include "vdv_SubscriberTimeService.h"

namespace vdv301 {

    class SntpAutoConfig : public Observer<TimeConfiguration>
    {
    private:
        SubscriberTimeService& m_TimeService;
        std::string m_LastSntpServer;

    public:
        SntpAutoConfig(SubscriberTimeService& timeService);
        ~SntpAutoConfig();

        void OnChanged(const TimeConfiguration* result) override;

    private:
        static void OnTimeSyncCallback(struct timeval* tv);
    };
}