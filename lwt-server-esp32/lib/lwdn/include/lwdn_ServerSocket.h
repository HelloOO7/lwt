#pragma once

#include "lwdn_Socket.h"
#include "lwdn_Link.h"
#include <memory>

namespace lwdn {

    class ServerSocket : public LinkObject {
    public:
        virtual ~ServerSocket() = default;

        virtual std::unique_ptr<Socket> Accept() = 0;
    };
}