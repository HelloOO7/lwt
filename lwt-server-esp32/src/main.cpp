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
#include "lwt_TripInformationService.h"
#include "lwt_TicketValidationService.h"
#include "lwtp_StartTLSInterceptor.h"
#include "lwt_AdvData.h"
#include "lwdn_BleAdvertiser.h"
#include "lwt_TripInfoAdvertiser.h"
#include "lwt_PreauthorizationTokenManager.h"
#include "lwt_TicketSignatureVerifier.h"
#include "lwt_MosClient.h"
#include "BitConverter.h"
#include "operations_generated.h"
#include "esp_event.h"
#include "tls_setup.h"
#include "tls_certs.h"
#include "ticket_pubkey.h"
#include "debug_device.h"
#include <atomic>

static constexpr uint16_t BLE_PSM = 0xD7; // 0x80 + 'W'
static constexpr lwdn::BleAdvertiser::UUID32 BLE_SERVICE_UUID_VEHICLE = 0x4C575456; // 'LWTV'
static constexpr lwdn::BleAdvertiser::UUID32 BLE_SERVICE_UUID_STOP = 0x4C575453; // 'LWTS'
static constexpr lwdn::BleAdvertiser::UUID32 BLE_SERVICE_UUID_VEHICLE_EXTENDED = BLE_SERVICE_UUID_VEHICLE + 'E';
static constexpr lwdn::BleAdvertiser::UUID32 BLE_SERVICE_UUID_STOP_EXTENDED = BLE_SERVICE_UUID_STOP + 'E';

static const lwt::TicketValidationConfig TICKETING_CONFIG = {
    .TariffSystemID = "PID",
    .PreauthorizationGracePeriodUs = 2 * 60 * 1000 * 1000, // 2 minutes
    .ValidationProtectionPeriodUs = 1 * 60 * 1000 * 1000, // 1 minute
};

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
    lwt::TripInformationService m_TripInfoService;
    lwt::PreauthorizationTokenManager m_PreauthTokenManager;
    lwt::TicketSignatureVerifier m_TicketVerifier;
    lwt::MOSClient m_MOSClient;
    lwt::TicketValidationService m_TicketService;

    lwdn::BleAdvertiser m_BLETripAdvertiserLegacy;
    lwdn::BleAdvertiser m_BLETripAdvertiserExt;
    lwt::TripInfoAdvertiser m_TripInfoAdvertiser;

public:
    AppMain() :
        m_TlsCredentials(
            get_debug_device_crt_start(), get_debug_device_crt_end(),
            TLS_LWT_SERVER_KEY_DEBUG_START, TLS_LWT_SERVER_KEY_DEBUG_END
        ),
        m_HttpServiceDiscovery{ vdv301::HttpServiceDiscovery() },
        m_CISSubscriber(
            m_HttpServiceDiscovery,
            vdv301::SubscriberCIS::Operation::GetAllData
        ),
        m_TVSSubscriber(
            m_HttpServiceDiscovery,
            vdv301::SubscriberTVS::Operation::GetRazzia | vdv301::SubscriberTVS::Operation::GetCurrentTariffStop
        ),
        m_BLEServer(BLE_PSM, lwtp::MAX_PACKET_SIZE),
        m_ServiceRegistry(lwt::Operation_MIN, lwt::Operation_MAX),
        m_AppServer(m_ServiceRegistry),
        m_ServerAuthService(get_debug_device_crt_start(), m_TlsCredentials.device_key, m_TlsCredentials.ctr_drbg),
        m_TripInfoService(m_CISSubscriber),
        m_PreauthTokenManager(LoadOrCreateHmacKey(m_TlsCredentials.ctr_drbg, "pat_hmac_key", 32)),
        m_TicketVerifier(),
        m_MOSClient("https://ticketing.mos.ropid:8080", {get_debug_device_crt_start(), get_debug_device_crt_end()}, {TLS_LWT_SERVER_KEY_DEBUG_START, TLS_LWT_SERVER_KEY_DEBUG_END}),
        m_TicketService(TICKETING_CONFIG, m_PreauthTokenManager, m_TicketVerifier, m_MOSClient, m_TripInfoService, &m_TVSSubscriber), //TVS not yet implemented
        m_BLETripAdvertiserLegacy(0, BLE_SERVICE_UUID_VEHICLE, lwdn::BleAdvertiser::Flags::INCLUDE_DEVICE_NAME | lwdn::BleAdvertiser::Flags::USE_LEGACY_ADVERTISING),
        m_BLETripAdvertiserExt(1, BLE_SERVICE_UUID_VEHICLE_EXTENDED, lwdn::BleAdvertiser::Flags::INCLUDE_DEVICE_NAME),
        m_TripInfoAdvertiser(m_CISSubscriber, { &m_BLETripAdvertiserLegacy, &m_BLETripAdvertiserExt })
    {
        lwt::ensure_generated_types_linked();

        setup_tls_config(m_TlsCredentials, m_MbedTlsConfig);

        m_TicketVerifier.RegisterPublicKey(0, {TICKET_SIGNING_KEY_PUB_START, TICKET_SIGNING_KEY_PUB_END});

        m_ServiceRegistry.RegisterServices(m_PingService, m_ServerAuthService, m_TripInfoService, m_TicketService);

        m_AppServer.AddInterceptor(std::make_unique<lwtp::StartTLSInterceptor>(m_MbedTlsConfig));
        m_AppServer.AddSocket(&m_BLEServer, 1, 6144);
    }

    void StartAdvertising() {
        m_BLETripAdvertiserLegacy.Start();
        m_BLETripAdvertiserExt.Start();
    }

private:
    static std::vector<uint8_t> LoadOrCreateHmacKey(mbedtls_ctr_drbg_context& ctrDrbg, const char* nvsKey, size_t expectedSize) {
        nvs_handle nvsHandle;
        ESP_ERROR_CHECK(nvs_open("lwt", NVS_READWRITE, &nvsHandle));
        std::vector<uint8_t> hmacKey(expectedSize);
        size_t pBlobSize = expectedSize;
        esp_err_t err = nvs_get_blob(nvsHandle, nvsKey, hmacKey.data(), &pBlobSize);
        if (err == ESP_ERR_NVS_NOT_FOUND) {
            // generate new key
            int rc = mbedtls_ctr_drbg_random(&ctrDrbg, hmacKey.data(), expectedSize);
            assert(rc == 0);
            ESP_ERROR_CHECK(nvs_set_blob(nvsHandle, nvsKey, hmacKey.data(), expectedSize));
            ESP_ERROR_CHECK(nvs_commit(nvsHandle));
        }
        else {
            ESP_ERROR_CHECK(err);
        }
        nvs_close(nvsHandle);
        return hmacKey;
    }
};

AppMain* g_AppMain = nullptr;

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
    struct ble_gap_ext_adv_params adv_params;
    struct ble_hs_adv_fields fields;
    const char* name;
    int rc;

    uint8_t gap_instance = 0;

    memset(&adv_params, 0, sizeof(adv_params));

    adv_params.connectable = true;
    adv_params.scannable = true;
    adv_params.legacy_pdu = true;
    adv_params.anonymous = false;
    adv_params.own_addr_type = BLE_OWN_ADDR_PUBLIC;

    rc = ble_gap_ext_adv_configure(gap_instance, &adv_params, 0, bleprph_gap_event, 0);
    if (rc != 0) {
        MODLOG_DFLT(ERROR, "error configuring advertisement; rc=%d\n", rc);
        return;
    }

    memset(&fields, 0, sizeof(fields));
    // normally it is not mandatory to provide a device name and we could in theory just read the raw scan data
    // and match the service UUID.
    // however, in order to allow background scans on Android 8+, we need to use a ScanFilter, which
    // does not work reliably over service data on a lot of devices, thereby mandating the use of the
    // less bug-prone name field. This also limits our advertisement size to 20 bytes on BLE 4.x.
    name = ble_svc_gap_device_name();
    fields.name = (uint8_t*)name;
    fields.name_len = strlen(name);
    fields.name_is_complete = 1;
    uint8_t svc_data[sizeof(uint32_t) + lwt::AdvDataBasic::PACKED_SIZE];
    BitConverter<std::endian::little>::FromUInt32(BLE_SERVICE_UUID_VEHICLE, svc_data);
    lwt::AdvDataBasic test_adv_data{
        .line_type = lwt::LineType::LineType_GenericBus,
        .line_license_number = 100394,
        .trip_number = 1001,
        .direction_cis_number = 27882,
        .stop_cis_number = 1054,
        .stop_arrival_time = 9 * 60 + 10,
        .stop_departure_time = 9 * 60 + 11,
        .delay = -1,
        .flags = lwt::AdvDataBasic::FLAG_IS_AT_STOP
    };
    test_adv_data.pack(svc_data + sizeof(uint32_t));
    fields.svc_data_uuid32 = svc_data;
    fields.svc_data_uuid32_len = sizeof(svc_data);

    os_mbuf* adv_data = os_msys_get_pkthdr(BLE_HS_ADV_MAX_SZ, 0);
    assert(adv_data);
    rc = ble_hs_adv_set_fields_mbuf(&fields, adv_data);
    if (rc != 0) {
        MODLOG_DFLT(ERROR, "error encoding advertisement data; rc=%d\n", rc);
        return;
    }

    rc = ble_gap_ext_adv_set_data(gap_instance, adv_data);
    if (rc != 0)
    {
        MODLOG_DFLT(ERROR, "error setting advertisement data; rc=%d\n", rc);
        return;
    }

    /* Begin advertising. */
    rc = ble_gap_ext_adv_start(gap_instance, 0, 0);
    if (rc != 0)
    {
        MODLOG_DFLT(ERROR, "error enabling advertisement; rc=%d\n", rc);
        return;
    }
}

static void bleprph_readvertise(void) {
    if (!ble_gap_ext_adv_active(0)) {
        int rc = ble_gap_ext_adv_start(0, 0, 0);
        if (rc != 0)
        {
            MODLOG_DFLT(ERROR, "error re-enabling advertisement; rc=%d\n", rc);
            return;
        }
    }
}

bool ble_synced{ false };

static void bleprph_on_sync(void)
{
    ble_synced = true;
    if (g_AppMain) {
        g_AppMain->StartAdvertising();
    }
    //bleprph_advertise();
}

void init_nimble() {
    ESP_ERROR_CHECK(nimble_port_init());

    ble_hs_cfg.sync_cb = bleprph_on_sync;

    ble_svc_gap_init();
    ble_svc_gap_device_name_set("LWT");
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

    g_AppMain = new AppMain();
    if (ble_synced) {
        g_AppMain->StartAdvertising();
    }

    while (true) {
        vTaskDelay(pdMS_TO_TICKS(1000));
    }

    delete g_AppMain;
}