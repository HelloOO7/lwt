#pragma once

#include "lwdn_Link.h"

namespace lwdn {

    class WifiNanLinkAdapter : public LinkAdapter {
    public:
        virtual LinkType GetLinkType() const override { return LinkType::WIFI_NAN; }
        virtual LinkAddress GetLinkAddress() const override;
    };

    extern WifiNanLinkAdapter WIFI_NAN_ADAPTER;
}