#pragma once

#include <span>
#include <cstdint>
#include "lwdn_Link.h"
#include "CommonTypes.h"

namespace lwdn {

    class Advertiser : public LinkObject
    {
    public:
        virtual ~Advertiser() = default;

        virtual void Start() = 0;
        virtual void Stop() = 0;
        virtual bool IsAdvertising() const = 0;

        virtual size_t GetMaxAdvDataSize() const = 0;
        virtual bool SetLwdnAdvData(const ByteSpan& data) = 0;
    };
}