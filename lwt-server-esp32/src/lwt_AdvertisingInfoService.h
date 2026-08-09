#pragma once

#include "lwt_ServiceRegistry.h"
#include "lwt_TripInfoAdvertiser.h"

namespace lwt {

    class AdvertisingInfoService {
    private:
        TripInfoAdvertiser& m_Advertiser;

    public:
        AdvertisingInfoService(TripInfoAdvertiser& advertiser);

        void Register(ServiceRegistry& registry);
    };
}