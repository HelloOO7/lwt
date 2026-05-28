#include <iostream>
#include "vdv_ServiceDiscovery.h"
#include "vdv_SubscriberCIS.h"
#include "nvs_flash.h"
#include "wifi_client.h"
#include "wifi_secrets.h"

class AppMain {
private:
    vdv301::ServiceDiscovery m_HttpServiceDiscovery;
    vdv301::SubscriberCIS m_CISSubscriber;

public:
    AppMain() :
        m_HttpServiceDiscovery{ vdv301::HttpServiceDiscovery() },
        m_CISSubscriber(
            m_HttpServiceDiscovery,
            vdv301::SubscriberCIS::Operation::GetCurrentStopPoint | vdv301::SubscriberCIS::Operation::GetCurrentAnnouncement
        )
    {

    }
};

void init_nvs() {
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);
}

extern "C" void app_main() {
    init_nvs();
    std::cout << "Hello, VDV301!" << std::endl;
    wifi_init_sta(WIFI_SSID, WIFI_PASSWORD, 1);

    AppMain* app = new AppMain();

    while (true) {
        vTaskDelay(pdMS_TO_TICKS(1000));
    }

    delete app;
}