#include "lwt_ServiceRegistry.h"

namespace lwt {

    OperationResult ServiceRegistry::NoOpOperation(const RequestPacket& request) {
        return 200; // OK by default
    }
}