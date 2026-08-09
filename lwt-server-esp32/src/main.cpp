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
#include "psa/crypto.h"
#include "ticket_pubkey.h"
#include "debug_device.h"
#include "esp_nan.h"
#include "lwdn_WifiNanPublisher.h"
#include "lwdn_WifiNanAdvertiser.h"
#include "lwdn_WifiNanServer.h"
#include "NewAndDelete.h"
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
    mbedtls_ssl_config m_MbedTlsConfig;
    vdv301::ServiceDiscovery m_HttpServiceDiscovery;
    vdv301::SubscriberCIS m_CISSubscriber;
    vdv301::SubscriberTVS m_TVSSubscriber;
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
        m_HttpServiceDiscovery{ vdv301::HttpServiceDiscovery() },
        m_CISSubscriber(
            m_HttpServiceDiscovery,
            vdv301::SubscriberCIS::Operation::GetAllData
        ),
        m_TVSSubscriber(
            m_HttpServiceDiscovery,
            vdv301::SubscriberTVS::Operation::GetRazzia | vdv301::SubscriberTVS::Operation::GetCurrentTariffStop
        ),
        m_ServiceRegistry(lwt::Operation_MIN, lwt::Operation_MAX),
        m_AppServer(m_ServiceRegistry),
        m_ServerAuthService(get_debug_device_crt_start(), m_TlsCredentials.device_key),
        m_TripInfoService(m_CISSubscriber),
        m_PreauthTokenManager(LoadOrCreateHmacKey("pat_hmac_key", 32)),
        m_TicketVerifier(),
        m_MOSClient("https://ticketing.mos.ropid:8080", { get_debug_device_crt_start(), get_debug_device_crt_end() }, { TLS_LWT_SERVER_KEY_DEBUG_START, TLS_LWT_SERVER_KEY_DEBUG_END }),
        m_TicketService(TICKETING_CONFIG, m_PreauthTokenManager, m_TicketVerifier, m_MOSClient, m_TripInfoService, &m_TVSSubscriber), //TVS not yet implemented
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
        m_TripInfoAdvertiser(m_CISSubscriber, { &m_BLETripAdvertiserLegacy, &m_BLETripAdvertiserExt, &m_WifiNanAdvertiser })
    {
        lwt::ensure_generated_types_linked();

        setup_tls_config(m_TlsCredentials, m_MbedTlsConfig);

        m_TicketVerifier.RegisterPublicKey(0, { TICKET_SIGNING_KEY_PUB_START, TICKET_SIGNING_KEY_PUB_END });

        m_ServiceRegistry.RegisterServices(m_PingService, m_ServerAuthService, m_TripInfoService, m_TicketService);

        m_AppServer.AddInterceptor(std::make_unique<lwtp::StartTLSInterceptor>(m_MbedTlsConfig));
        m_AppServer.AddSocket(&m_BLEServer, 1, 6144);
        m_AppServer.AddSocket(&m_WifiNanServer, 1, 6144);
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

#include <sys/socket.h>

void tcp_server_app_main(void);

lwdn::WifiNanPublisher nanPublisher(
    {
        .ServiceName = "_ESP-Demo._udp",
        .ServiceType = NAN_PUBLISH_UNSOLICITED,
        .Features = lwdn::WifiNanPublisher::Feature::DATAPATH
    }
);

void test_server_task(void* param) {
    constexpr const char* TAG = "testTCPServerMain";

    lwdn::WifiNanServer nanServer(nanPublisher, 3333);

    while (true) {
        ESP_LOGI("testTCPServerMain", "Waiting for incoming connection on port %d...", 3333);
        auto socket = nanServer.Accept();
        if (socket) {
            char buf[128];
            size_t receivedLen;
            int err = socket->Read(buf, sizeof(buf), &receivedLen, 5000);
            if (err == 0) {
                ESP_LOGI("testTCPServerMain", "Received %d bytes: %.*s", receivedLen, (int)receivedLen, buf);
                size_t sentLen;
                err = socket->Write(buf, receivedLen, &sentLen);
                if (err == 0) {
                    ESP_LOGI("testTCPServerMain", "Sent %d bytes back to client", sentLen);
                } else {
                    ESP_LOGE("testTCPServerMain", "Error occurred during sending: errno %d", err);
                }
            } else {
                ESP_LOGE("testTCPServerMain", "Error occurred during receive: errno %d", err);
            }
        } else {
            ESP_LOGI("testTCPServerMain", "Accept returned null socket, exiting accept loop");
            break;
        }
    }

    /*char rx_buffer[128];
    char addr_str[INET6_ADDRSTRLEN];
    struct sockaddr_in6 dest_addr;

    while (1) {
        bzero(&dest_addr, sizeof(dest_addr));
        dest_addr.sin6_family = AF_INET6;
        dest_addr.sin6_port = htons(3333);

        int sock = socket(AF_INET6, SOCK_STREAM, IPPROTO_IPV6);
        if (sock < 0) {
            ESP_LOGE(TAG, "Unable to create socket: errno %d", errno);
            break;
        }
        ESP_LOGI(TAG, "Socket created");

        struct timeval timeout = { .tv_sec = 10, .tv_usec = 0 };
        setsockopt(sock, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));

        int err = bind(sock, (struct sockaddr*)&dest_addr, sizeof(dest_addr));
        if (err < 0) {
            ESP_LOGE(TAG, "Socket unable to bind: errno %d", errno);
        }
        ESP_LOGI(TAG, "Socket bound, port %d", 3333);

        err = listen(sock, 1);
        if (err < 0) {
            ESP_LOGE(TAG, "Error occurred during listen: errno %d", errno);
            break;
        }

        struct sockaddr_in6 source_addr;
        socklen_t socklen = sizeof(source_addr);

        while (1) {
            ESP_LOGI(TAG, "Waiting for data");
            int client_sock = accept(sock, (struct sockaddr*)&source_addr, &socklen);
            if (client_sock < 0) {
                if (errno == EAGAIN) {
                    continue; // timeout, go back to waiting for data
                }
                ESP_LOGE(TAG, "Unable to accept connection: errno %d", errno);
                break;
            }
            inet6_ntoa_r(source_addr.sin6_addr, addr_str, sizeof(addr_str) - 1);
            ESP_LOGI(TAG, "Socket accepted ip6=%s", addr_str);

            int recv_len = recv(client_sock, rx_buffer, sizeof(rx_buffer), 0);
            if (recv_len < 0) {
                ESP_LOGE(TAG, "Error occurred during receive: errno %d", errno);
                break;
            }
            ESP_LOGI(TAG, "Received %d bytes: %.*s", recv_len, recv_len, rx_buffer);

            // send back the same data to the client
            int to_write = recv_len;
            int written = send(client_sock, rx_buffer, to_write, 0);
            if (written < 0) {
                ESP_LOGE(TAG, "Error occurred during sending: errno %d", errno);
                break;
            }
            ESP_LOGI(TAG, "Sent %d bytes back to client", written);

            shutdown(client_sock, SHUT_RDWR);
            close(client_sock);
        }

        if (sock != -1) {
            ESP_LOGE(TAG, "Shutting down socket and restarting...");
            shutdown(sock, SHUT_RDWR);
            close(sock);
        }
    }*/
}

static void got_ip6_handler(void* arg, esp_event_base_t event_base,
    int32_t event_id, void* event_data)
{
    static bool s_server_started = false;

    ip_event_got_ip6_t* event = (ip_event_got_ip6_t*)event_data;

    /* Only act on the NAN datapath interface; ignore GOT_IP6 from other netifs. */
    if (event->esp_netif != esp_netif_get_handle_from_ifkey("WIFI_NAN_DEF")) {
        return;
    }

    if (s_server_started) {
        return;
    }
    s_server_started = true;

    xTaskCreate(test_server_task, "tcp_server", 4096, NULL, 5, NULL);
}

void testTCPServerMain() {
    ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_GOT_IP6, &got_ip6_handler, NULL));

    nanPublisher.Publish();
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