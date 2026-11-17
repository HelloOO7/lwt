#include "SystemTime.h"
#include "esp_event.h"
#include "esp_err.h"
#include "esp_netif_sntp.h"

bool g_TimeSyncCompleted;

void SystemTime::Init() {
    ESP_ERROR_CHECK(esp_event_handler_register(
        NETIF_SNTP_EVENT,
        NETIF_SNTP_TIME_SYNC,
        [](void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data) {
            if (event_id == NETIF_SNTP_TIME_SYNC) {
                g_TimeSyncCompleted = true;
            }
        },
        nullptr
    ));
}

bool SystemTime::IsSynchronized() {
    return g_TimeSyncCompleted;
}