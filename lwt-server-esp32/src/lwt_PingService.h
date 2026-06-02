#pragma once

#include "lwt_ApplicationServer.h"
#include "lwt_ServiceRegistry.h"

namespace lwt {

    class PingService {
    public:
        void Register(ServiceRegistry& registry);
    };
}