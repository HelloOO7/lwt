#include "lwdn_WifiNanLink.h"

#include "esp_wifi.h"
#include "esp_log.h"

namespace lwdn {

    static constexpr const char* TAG = "WifiNanLinkAdapter";

    LinkAddress WifiNanLinkAdapter::GetLinkAddress() const {
        LinkAddress addr{};
        int rc = esp_wifi_get_mac(WIFI_IF_NAN, addr.data());
        if (rc != 0) {
            ESP_LOGE(TAG, "error getting Wi-Fi NAN address; rc=%d\n", rc);
        }
        return addr;
    }

    WifiNanLinkAdapter WIFI_NAN_ADAPTER;
}