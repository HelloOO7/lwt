#include <iostream>
#include "lwt_schemas.h"
#include "vdv_ServiceDiscovery.h"
#include "vdv_SubscriberCIS.h"
#include "vdv_SubscriberTVS.h"
#include "nvs_flash.h"
#include "wifi_client.h"
#include "ethernet_client.h"
#include "wifi_secrets.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "services/gap/ble_svc_gap.h"
#include "host/ble_hs.h"
#include "lwdn_BleL2CapServer.h"
#include "lwtp_Protocol.h"
#include "lwt_ServiceRegistry.h"
#include "lwt_ApplicationServer.h"
#include "lwt_PingService.h"
#include "lwt_ServerAuthenticationService.h"
#include "lwtp_StartTLSInterceptor.h"
#include "operations_generated.h"
#include "esp_event.h"
#include "tls_setup.h"
#include "tls_certs.h"

static constexpr uint16_t BLE_PSM = 0xD7; // 0x80 + 'W'

class AppMain {
private:
    TlsEnvironment m_TlsCredentials;
    mbedtls_ssl_config m_MbedTlsConfig;
    vdv301::ServiceDiscovery m_HttpServiceDiscovery;
    vdv301::SubscriberCIS m_CISSubscriber;
    vdv301::SubscriberTVS m_TVSSubscriber;
    lwdn::BleL2CapServer m_BLEServer;
    lwt::ServiceRegistry m_ServiceRegistry;
    lwt::ApplicationServer m_AppServer;
    lwt::PingService m_PingService;
    lwt::ServerAuthenticationService m_ServerAuthService;

public:
    AppMain() :
        m_TlsCredentials(TLS_DEVICE_CRT_START, TLS_DEVICE_CRT_END, TLS_LWT_SERVER_KEY_DEBUG_START, TLS_LWT_SERVER_KEY_DEBUG_END),
        m_HttpServiceDiscovery{ vdv301::HttpServiceDiscovery() },
        m_CISSubscriber(
            m_HttpServiceDiscovery/*,
            vdv301::SubscriberCIS::Operation::GetCurrentStopPoint | vdv301::SubscriberCIS::Operation::GetCurrentAnnouncement*/
        ),
        m_TVSSubscriber(
            m_HttpServiceDiscovery/*,
            vdv301::SubscriberTVS::Operation::GetRazzia | vdv301::SubscriberTVS::Operation::GetCurrentStopPoint*/
        ),
        m_BLEServer(BLE_PSM, lwtp::MAX_PACKET_SIZE),
        m_ServiceRegistry(lwt::Operation_MIN, lwt::Operation_MAX),
        m_AppServer(m_ServiceRegistry),
        m_ServerAuthService(TLS_DEVICE_CRT_START, m_TlsCredentials.device_key, m_TlsCredentials.ctr_drbg)
    {
        lwt::ensure_generated_types_linked();

        setup_tls_config(m_TlsCredentials, m_MbedTlsConfig);

        m_ServiceRegistry.RegisterServices(m_PingService, m_ServerAuthService);

        m_AppServer.AddInterceptor(std::make_unique<lwtp::StartTLSInterceptor>(m_MbedTlsConfig));
        m_AppServer.AddSocket(&m_BLEServer);
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

// examples also forward declare
extern "C" void ble_store_config_init(void);

void nimble_task(void* param)
{
    /* This function will return only when nimble_port_stop() is executed */
    nimble_port_run();

    nimble_port_freertos_deinit();
}

static void bleprph_readvertise(void);

static int bleprph_gap_event(struct ble_gap_event* event, void* arg)
{
    switch (event->type) {
    case BLE_GAP_EVENT_CONNECT:
        /* A new connection was established or a connection attempt failed. */
        if (event->connect.status == 0) {
            MODLOG_DFLT(INFO, "connection established; handle=%d\n", event->connect.conn_handle);
        }
        else {
            MODLOG_DFLT(INFO, "connection failed; status=%d\n", event->connect.status);
        }
        bleprph_readvertise();
        break;

    case BLE_GAP_EVENT_DISCONNECT:
        MODLOG_DFLT(INFO, "disconnect; reason=%d\n", event->disconnect.reason);
        bleprph_readvertise();
        break;

    case BLE_GAP_EVENT_ADV_COMPLETE:
        MODLOG_DFLT(INFO, "advertise complete\n");
        bleprph_readvertise();
        break;

    default:
        break;
    }
    return 0;
}

static void bleprph_advertise(void)
{
    struct ble_gap_adv_params adv_params;
    struct ble_hs_adv_fields fields;
    const char* name;
    int rc;

    /**
     *  Set the advertisement data included in our advertisements:
     *     o Flags (indicates advertisement type and other general info).
     *     o Advertising tx power.
     *     o Device name.
     *     o 16-bit service UUIDs (alert notifications).
     */

    memset(&fields, 0, sizeof fields);

    /* Advertise two flags:
     *     o Discoverability in forthcoming advertisement (general)
     *     o BLE-only (BR/EDR unsupported).
     */
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;

    /* Indicate that the TX power level field should be included; have the
     * stack fill this value automatically.  This is done by assigning the
     * special value BLE_HS_ADV_TX_PWR_LVL_AUTO.
     */
    fields.tx_pwr_lvl_is_present = 1;
    fields.tx_pwr_lvl = BLE_HS_ADV_TX_PWR_LVL_AUTO;

    name = ble_svc_gap_device_name();
    fields.name = (uint8_t*)name;
    fields.name_len = strlen(name);
    fields.name_is_complete = 1;

    /*fields.uuids16 = (ble_uuid16_t[]){ BLE_UUID16_INIT(GATT_SVR_SVC_ALERT_UUID) };
    fields.num_uuids16 = 1;
    fields.uuids16_is_complete = 1;*/

    rc = ble_gap_adv_set_fields(&fields);
    if (rc != 0)
    {
        MODLOG_DFLT(ERROR, "error setting advertisement data; rc=%d\n", rc);
        return;
    }

    /* Begin advertising. */
    memset(&adv_params, 0, sizeof adv_params);
    adv_params.conn_mode = BLE_GAP_CONN_MODE_UND;
    adv_params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    rc = ble_gap_adv_start(BLE_OWN_ADDR_PUBLIC, NULL, BLE_HS_FOREVER, &adv_params, bleprph_gap_event, NULL);
    if (rc != 0)
    {
        MODLOG_DFLT(ERROR, "error enabling advertisement; rc=%d\n", rc);
        return;
    }
}

static void bleprph_readvertise(void) {
    if (!ble_gap_adv_active()) {
        bleprph_advertise();
    }
}

static void bleprph_on_sync(void)
{
    bleprph_readvertise();
}

void init_nimble() {
    ESP_ERROR_CHECK(nimble_port_init());

    ble_hs_cfg.sync_cb = bleprph_on_sync;

    ble_svc_gap_init();
    ble_svc_gap_device_name_set("LWT ESP32");
    ble_svc_gap_device_appearance_set(0x08CB); // Bus

    ble_store_config_init();

    nimble_port_freertos_init(nimble_task);
}

extern "C" void app_main() {
    init_nvs();
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    init_nimble();
    std::cout << "Hello, VDV301!" << std::endl;
    esp_netif_init();
    //wifi_init_sta(WIFI_SSID, WIFI_PASSWORD, 1);
    ethernet_init();
    ethernet_init_netif();

    AppMain* app = new AppMain();

    while (true) {
        vTaskDelay(pdMS_TO_TICKS(1000));
    }

    delete app;
}