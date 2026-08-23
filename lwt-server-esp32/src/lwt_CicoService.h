#pragma once

#include "lwt_ServiceRegistry.h"

namespace lwt {

    class CicoService {
    private:
    public:
        CicoService(int syncTaskPriority);

        void Register(ServiceRegistry& registry);
    };
}