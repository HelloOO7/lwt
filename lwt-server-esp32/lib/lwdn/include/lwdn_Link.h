#pragma once

#include <array>
#include <cstdint>

namespace lwdn {

    enum class LinkType {
        BLE,
        WIFI_NAN
    };

    using LinkAddress = std::array<uint8_t, 6>;

    class LinkAdapter {
    public:
        virtual ~LinkAdapter() = default;

        virtual LinkType GetLinkType() const = 0;
        virtual LinkAddress GetLinkAddress() const = 0;
    };

    class LinkObject {
    public:
        virtual ~LinkObject() = default;

        virtual LinkAdapter* GetLinkAdapter() const = 0;
    };
}