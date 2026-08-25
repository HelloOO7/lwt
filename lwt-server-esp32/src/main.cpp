#include <iostream>
#include "lwt_schemas.h"
#include "vdv_ServiceDiscovery.h"
#include "vdv_SubscriberCIS.h"
#include "vdv_SubscriberTVS.h"
#include "vdv_PublisherRCS.h"
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
#include "lwtp_TLSInterceptor.h"
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
#include "psa/crypto.h"
#include "ticket_pubkey.h"
#include "debug_device.h"
#include "esp_nan.h"
#include "lwdn_WifiNanPublisher.h"
#include "lwdn_WifiNanAdvertiser.h"
#include "lwdn_WifiNanServer.h"
#include "NewAndDelete.h"
#include "lwt_CertRoleInterceptor.h"
#include "lwt_TaskPriorities.h"
#include "DigitalSignature.h"
#include <atomic>

static constexpr uint16_t BLE_PSM = 0xD7; // 0x80 + 'W'
static constexpr lwdn::BleAdvertiser::UUID32 BLE_SERVICE_UUID_VEHICLE = 0x4C575456; // 'LWTV'
static constexpr lwdn::BleAdvertiser::UUID32 BLE_SERVICE_UUID_STOP = 0x4C575453; // 'LWTS'
static constexpr lwdn::BleAdvertiser::UUID32 BLE_SERVICE_UUID_VEHICLE_EXTENDED = BLE_SERVICE_UUID_VEHICLE + 'E';
static constexpr lwdn::BleAdvertiser::UUID32 BLE_SERVICE_UUID_STOP_EXTENDED = BLE_SERVICE_UUID_STOP + 'E';

static constexpr in_port_t WIFI_NAN_PORT = 26001;
static const std::vector<psram_vector<uint8_t>> WIFI_NAN_MATCHING_FILTERS_VEHICLE = { {'V'}, {'*'} };
static const std::vector<psram_vector<uint8_t>> WIFI_NAN_MATCHING_FILTERS_STOP = { {'*'}, {'S'} };

static const lwt::TicketValidationConfig TICKETING_CONFIG = {
    .TariffSystemID = "PID",
    .PreauthorizationGracePeriodUs = 2 * 60 * 1000 * 1000, // 2 minutes
    .ValidationProtectionPeriodUs = 1 * 60 * 1000 * 1000, // 1 minute
};

class AppMain {
private:
    TlsEnvironment m_TlsCredentials;
    DigitalSignature m_SigningKey;
    mbedtls_ssl_config m_MbedTlsConfig;
    lwdn::TLSConfig m_TLSConfig;
    vdv301::ServiceDiscovery m_HttpServiceDiscovery;
    vdv301::SubscriberCIS m_CISSubscriber;
    vdv301::SubscriberTVS m_TVSSubscriber;
    vdv301::PublisherRCS m_RCSPublisher;
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
    lwdn::BleL2CapServer m_BLEServer;

    lwdn::WifiNanPublisher m_WifiNanPublisher;
    lwdn::WifiNanAdvertiser m_WifiNanAdvertiser;
    lwdn::WifiNanServer m_WifiNanServer;

    lwt::TripInfoAdvertiser m_TripInfoAdvertiser;

public:
    AppMain() :
        m_TlsCredentials(
            get_debug_device_crt_start(), get_debug_device_crt_end(),
            TLS_LWT_SERVER_KEY_DEBUG_START, TLS_LWT_SERVER_KEY_DEBUG_END
        ),
        m_SigningKey(ByteSpan(TLS_LWT_SERVER_KEY_DEBUG_START, TLS_LWT_SERVER_KEY_DEBUG_END), DigitalSignature::KeyUsage::SIGN),
        m_TLSConfig(m_MbedTlsConfig),
        m_HttpServiceDiscovery{ vdv301::HttpServiceDiscovery(TASK_PRIORITY_BACKGROUND_SYNC) },
        m_CISSubscriber(
            m_HttpServiceDiscovery,
            vdv301::SubscriberCIS::Operation::AllData
        ),
        m_TVSSubscriber(
            m_HttpServiceDiscovery,
            vdv301::SubscriberTVS::Operation::Razzia | vdv301::SubscriberTVS::Operation::CurrentTariffStop
        ),
        m_RCSPublisher(m_HttpServiceDiscovery),
        m_ServiceRegistry(lwt::Operation_MIN, lwt::Operation_MAX),
        m_AppServer(m_ServiceRegistry),
        m_ServerAuthService(get_debug_device_crt_start(), m_SigningKey),
        m_TripInfoService(m_CISSubscriber),
        m_PreauthTokenManager(LoadOrCreateHmacKey("pat_hmac_key", 32)),
        m_TicketVerifier(),
        m_MOSClient("https://ticketing.mos.ropid:8080", { get_debug_device_crt_start(), get_debug_device_crt_end() }, { TLS_LWT_SERVER_KEY_DEBUG_START, TLS_LWT_SERVER_KEY_DEBUG_END }),
        m_TicketService(TICKETING_CONFIG, m_PreauthTokenManager, m_TicketVerifier, m_MOSClient, m_TripInfoService, &m_TVSSubscriber, &m_RCSPublisher),
        m_BLETripAdvertiserLegacy(0, BLE_SERVICE_UUID_VEHICLE, lwdn::BleAdvertiser::Flags::INCLUDE_DEVICE_NAME | lwdn::BleAdvertiser::Flags::USE_LEGACY_ADVERTISING),
        m_BLETripAdvertiserExt(1, BLE_SERVICE_UUID_VEHICLE_EXTENDED, lwdn::BleAdvertiser::Flags::INCLUDE_DEVICE_NAME),
        m_BLEServer(BLE_PSM, lwtp::MAX_PACKET_SIZE),
        m_WifiNanPublisher(
            {
                .ServiceName = "LWT",
                .ServiceType = NAN_PUBLISH_UNSOLICITED,
                .MatchingFilters = WIFI_NAN_MATCHING_FILTERS_VEHICLE,
                .Features = lwdn::WifiNanPublisher::Feature::DATAPATH
            }
        ),
        m_WifiNanAdvertiser(m_WifiNanPublisher),
        m_WifiNanServer(m_WifiNanPublisher, WIFI_NAN_PORT),
        m_TripInfoAdvertiser(m_CISSubscriber, m_TVSSubscriber, { &m_BLETripAdvertiserLegacy, &m_BLETripAdvertiserExt, &m_WifiNanAdvertiser })
    {
        lwt::ensure_generated_types_linked();

        setup_tls_config(m_TlsCredentials, m_MbedTlsConfig);
        m_TLSConfig.EnableSessionTickets(&m_TlsCredentials.tickets);

        m_TicketVerifier.RegisterPublicKey(0, { TICKET_SIGNING_KEY_PUB_START, TICKET_SIGNING_KEY_PUB_END });

        m_ServiceRegistry.RegisterServices(m_PingService, m_ServerAuthService, m_TripInfoService, m_TicketService);

        m_AppServer.AddInterceptor(std::make_unique<lwtp::StartTLSInterceptor>(m_TLSConfig));
        m_AppServer.AddInterceptor(std::make_unique<lwt::CertRoleInterceptor>());

        lwtp::Server::SocketTaskConfig socketTaskConfig{
            .m_StackSize = 6144,
            .m_Priority = TASK_PRIORITY_CLIENT_SERVER
        };

        m_AppServer.AddSocket(&m_BLEServer, 1, socketTaskConfig);
        m_AppServer.AddSocket(&m_WifiNanServer, 1, socketTaskConfig);
    }

    void StartAdvertising() {
        m_BLETripAdvertiserLegacy.Start();
        m_BLETripAdvertiserExt.Start();
        m_WifiNanAdvertiser.Start();
    }

private:
    static std::vector<uint8_t> LoadOrCreateHmacKey(const char* nvsKey, size_t expectedSize) {
        nvs_handle nvsHandle;
        ESP_ERROR_CHECK(nvs_open("lwt", NVS_READWRITE, &nvsHandle));
        std::vector<uint8_t> hmacKey(expectedSize);
        size_t pBlobSize = expectedSize;
        esp_err_t err = nvs_get_blob(nvsHandle, nvsKey, hmacKey.data(), &pBlobSize);
        if (err == ESP_ERR_NVS_NOT_FOUND) {
            // generate new key
            int rc = psa_generate_random(hmacKey.data(), expectedSize);
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

bool ble_synced{ false };

static void bleprph_on_sync(void)
{
    ble_synced = true;
    if (g_AppMain) {
        g_AppMain->StartAdvertising();
    }
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
    InitNewAndDelete();
    init_nvs();
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    init_nimble();
    std::cout << "Hello, VDV301!" << std::endl;
    esp_netif_init();

    wifi_init_default();
    wifi_init_nan();
    //wifi_init_sta(WIFI_SSID, WIFI_PASSWORD, 1);

    ethernet_init();
    ethernet_init_netif();
    mdns_init();
    mdns_hostname_set("lwt-esp32");
    psa_crypto_init();

    std::cout << "Services initialized, free memory before app launch=" << esp_get_free_internal_heap_size() << " bytes" << std::endl;

    g_AppMain = new AppMain();
    if (ble_synced) {
        g_AppMain->StartAdvertising();
    }

    std::cout << "Application started, remaining memory=" << esp_get_free_internal_heap_size() << " bytes" << std::endl;

    while (true) {
        vTaskDelay(pdMS_TO_TICKS(1000));
        //std::cout << "Application running, remaining memory=" << esp_get_free_internal_heap_size() << " bytes" << std::endl;
    }

    delete g_AppMain;
}