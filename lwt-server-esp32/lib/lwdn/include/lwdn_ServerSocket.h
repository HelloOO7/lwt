#pragma once

#include "lwdn_Socket.h"
#include <memory>

namespace lwdn {

    class ServerSocket {
    public:
        virtual ~ServerSocket() = default;

        virtual std::unique_ptr<Socket> Accept() = 0;
    };
}