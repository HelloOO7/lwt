#pragma once

#include <span>
#include <cstdint>

namespace lwdn {

    class Advertiser
    {
    public:
        virtual ~Advertiser() = default;

        virtual void Start() = 0;
        virtual void Stop() = 0;
        virtual bool IsAdvertising() const = 0;

        virtual size_t GetMaxAdvDataSize() const = 0;
        virtual bool SetLwdnAdvData(const std::span<const uint8_t>& data) = 0;
    };
}