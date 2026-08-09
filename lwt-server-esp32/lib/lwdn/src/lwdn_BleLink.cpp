#include "lwdn_BleLink.h"

#include "host/ble_hs_id.h"
#include "host/ble_hs.h"
#include <algorithm>

namespace lwdn {

    LinkAddress BleLinkAdapter::GetLinkAddress() const {
        LinkAddress addr{};
        int rc = ble_hs_id_copy_addr(BLE_ADDR_PUBLIC, addr.data(), nullptr);
        if (rc != 0) {
            MODLOG_DFLT(ERROR, "error getting BLE address; rc=%d\n", rc);
        }
        std::reverse(addr.begin(), addr.end()); // nimble returns address in reverse
        return addr;
    }

    BleLinkAdapter BLE_ADAPTER;
}