#include "vdv_SntpAutoConfig.h"

#include "esp_sntp.h"
#include "esp_log.h"
#include "esp_netif_sntp.h"
#include <cstdlib>

namespace vdv301 {

    static constexpr const char* TAG = "SntpAutoConfig";

    SntpAutoConfig::SntpAutoConfig(SubscriberTimeService& timeService) :
        m_TimeService(timeService)
    {
        m_TimeService.ObserveTimeConfiguration(*this);
    }

    SntpAutoConfig::~SntpAutoConfig()
    {
        m_TimeService.RemoveObserver(*this);
    }

    void SntpAutoConfig::OnChanged(const TimeConfiguration* result)
    {
        if (result) {
            if (result->GetSntpServer() != m_LastSntpServer) {
                m_LastSntpServer = result->GetSntpServer();
                // sntp_setservername() does not duplicate the string, so we need to keep a copy of it
                const char* sntpServer = m_LastSntpServer.c_str();
                esp_sntp_config_t config = ESP_NETIF_SNTP_DEFAULT_CONFIG(sntpServer);
                config.smooth_sync = true;
                config.sync_cb = OnTimeSyncCallback;
                esp_err_t err = esp_netif_sntp_init(&config);
                if (err == ESP_OK) {
                    ESP_LOGI(TAG, "SNTP auto-configured with server %s", sntpServer);
                }
                else {
                    if (err == ESP_ERR_INVALID_STATE) {
                        // already running, reconfigure it
                        esp_sntp_setservername(0, sntpServer);
                    }
                    else {
                        ESP_ERROR_CHECK(err);
                    }
                }
            }
            const char* oldTZ = getenv("TZ");
            const char* newTZ = result->GetProlepticTZ().c_str();
            if (!oldTZ || strcmp(oldTZ, newTZ) != 0) {
                ESP_LOGI(TAG, "Set timezone to %s", newTZ);
                setenv("TZ", newTZ, 1);
                tzset();
            }
        }
        else {
            ESP_LOGW(TAG, "Time configuration lost");
            esp_netif_sntp_deinit();
        }
    }

    void SntpAutoConfig::OnTimeSyncCallback(struct timeval* tv)
    {
        // print local date/time
        time_t now = tv->tv_sec;
        struct tm timeinfo;
        localtime_r(&now, &timeinfo);

        char strftime_buf[64];
        strftime(strftime_buf, sizeof(strftime_buf), "%c", &timeinfo);

        ESP_LOGI(TAG, "Time synchronized: %s, timezone: %s", strftime_buf, getenv("TZ"));
    }
}