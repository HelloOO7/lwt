#pragma once

#include "lwdn_Link.h"

namespace lwdn {

    class BleLinkAdapter : public LinkAdapter {
    public:
        virtual LinkType GetLinkType() const override { return LinkType::BLE; }
        virtual LinkAddress GetLinkAddress() const override;
    };

    extern BleLinkAdapter BLE_ADAPTER;
}