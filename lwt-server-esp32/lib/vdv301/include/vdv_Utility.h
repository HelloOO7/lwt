#pragma once

#include <cstdint>
#include "PSRAMContainers.h"
#include "esp_netif_types.h"

namespace vdv301
{

    uint32_t HashResponseWithoutTimestamp(const char* responseXml);

    inline uint32_t HashResponseWithoutTimestamp(const std::string& responseXml) {
        return HashResponseWithoutTimestamp(responseXml.c_str());
    }

    inline uint32_t HashResponseWithoutTimestamp(const psram_string& responseXml) {
        return HashResponseWithoutTimestamp(responseXml.c_str());
    }

    std::string IPToString(const esp_ip4_addr_t* ip);
}