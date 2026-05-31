#pragma once

#include <cstdint>

namespace lwdn {

    class Socket {
    public:
        virtual ~Socket() = default;

        virtual int Write(const void* data, size_t len, size_t* sentLen = nullptr) = 0;
        virtual int Read(void* buffer, size_t len, size_t* receivedLen = nullptr, size_t timeout = SIZE_MAX) = 0;

        int ReadFully(void* buffer, size_t len, size_t timeout = SIZE_MAX);
    };
}