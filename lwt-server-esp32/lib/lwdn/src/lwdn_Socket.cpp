#include "lwdn_Socket.h"

#include "esp_log.h"
#include <cerrno>

namespace lwdn {

    int Socket::ReadFully(void* buffer, size_t len, size_t timeout)
    {
        if (len == 0) {
            return 0;
        }
        uint8_t* buf = static_cast<uint8_t*>(buffer);
        size_t totalReceived = 0;
        while (totalReceived < len) {
            size_t receivedLen;
            int err = Read(buf + totalReceived, len - totalReceived, &receivedLen, timeout);
            if (err != 0) {
                return err;
            }
            if (receivedLen == 0) {
                return ECONNRESET;
            }
            totalReceived += receivedLen;
        }
        return 0;
    }
}