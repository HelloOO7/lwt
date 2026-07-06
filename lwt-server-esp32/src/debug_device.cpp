#include "debug_device.h"

#include "host/ble_hs_id.h"
#include "esp_log.h"
#include "tls_certs.h"

static const uint8_t BLE_ADDRESS_A[] = { 0xd0, 0xcf, 0x13, 0xe2, 0x2e, 0xd2 };
static const uint8_t BLE_ADDRESS_B[] = { 0x3c, 0xdc, 0x75, 0x83, 0x07, 0xde };

bool g_GotDebugDeviceID = false;
DebugDeviceID g_DebugDeviceID = DebugDeviceID::NONE;

bool compare_mac(const uint8_t* nimble_mac, const uint8_t* check_mac) {
    // address from nimble is in reverse order, so we need to compare in reverse
    for (int i = 0; i < 6; ++i) {
        if (nimble_mac[i] != check_mac[5 - i]) {
            return false;
        }
    }
    return true;
}

DebugDeviceID resolve_debug_device_id() {
    uint8_t addr[6];
    if (ble_hs_id_copy_addr(BLE_ADDR_PUBLIC, addr, nullptr) == 0) {
        if (compare_mac(addr, BLE_ADDRESS_A)) {
            return DebugDeviceID::ALICE;
        }
        else if (compare_mac(addr, BLE_ADDRESS_B)) {
            return DebugDeviceID::BOB;
        }
        else {
            ESP_LOGW("DebugDevice", "Unknown BLE address: %02X:%02X:%02X:%02X:%02X:%02X", addr[0], addr[1], addr[2], addr[3], addr[4], addr[5]);
        }
    }
    else {
        ESP_LOGE("DebugDevice", "Failed to get BLE address");
    }
    return DebugDeviceID::NONE;
}

DebugDeviceID get_debug_device_id() {
    if (!g_GotDebugDeviceID) {
        g_DebugDeviceID = resolve_debug_device_id();
        g_GotDebugDeviceID = true;
    }
    return g_DebugDeviceID;
}

uint8_t* get_debug_device_crt_start() {
    switch (get_debug_device_id()) {
    case DebugDeviceID::ALICE:
        return TLS_DEVICE_CRT_A_START;
    case DebugDeviceID::BOB:
        return TLS_DEVICE_CRT_B_START;
    default:
        ESP_LOGW("DebugDevice", "Unknown debug device ID, defaulting to Alice's certificate");
        return TLS_DEVICE_CRT_A_START;
    }
}

uint8_t* get_debug_device_crt_end() {
    switch (get_debug_device_id()) {
    case DebugDeviceID::ALICE:
        return TLS_DEVICE_CRT_A_END;
    case DebugDeviceID::BOB:
        return TLS_DEVICE_CRT_B_END;
    default:
        ESP_LOGW("DebugDevice", "Unknown debug device ID, defaulting to Alice's certificate");
        return TLS_DEVICE_CRT_A_END;
    }
}