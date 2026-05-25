#pragma once

#include "vdv_SubscriberHttp.h"

namespace vdv301
{

    class SubscriberCIS : public SubscriberHttp
    {
    public:
        SubscriberCIS(ServiceDiscovery& sd);

    protected:
        void OnSubscribe() override;
        void OnOperationResult(const std::string& operation, const std::string& result) override;
    };
}