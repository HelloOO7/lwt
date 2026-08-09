#include "lwdn_WifiNanServer.h"

#include "esp_log.h"
#include "lwdn_InetSocket.h"
#include "lwdn_WifiNanLink.h"
#include "esp_nan.h"
#include "esp_netif.h"
#include <sys/socket.h>

namespace lwdn {

    static constexpr const char* TAG = "WifiNanServer";

    WifiNanServer::WifiNanServer(WifiNanPublisher& publisher, in_port_t port) :
        m_Publisher(publisher),
        m_Port(port)
    {
        ESP_ERROR_CHECK(esp_event_handler_instance_register(IP_EVENT, IP_EVENT_GOT_IP6, &WifiNanServer::GotIP6Callback, this, &m_GetIP6EventInstance));
        ESP_ERROR_CHECK(esp_event_handler_instance_register(WIFI_EVENT, WIFI_EVENT_NDP_CONFIRM, &WifiNanServer::NanConfirmCallback, this, &m_NanConfirmEventInstance));
        ESP_ERROR_CHECK(esp_event_handler_instance_register(WIFI_EVENT, WIFI_EVENT_NDP_INDICATION, &WifiNanServer::NanIndicateCallback, this, &m_NanIndicateEventInstance));
    }

    WifiNanServer::~WifiNanServer()
    {
        {
            std::lock_guard<std::mutex> lock(m_SocketMutex);
            m_Closed = true;
            CloseServerSocket();
        }
        ESP_ERROR_CHECK(esp_event_handler_instance_unregister(IP_EVENT, IP_EVENT_GOT_IP6, m_GetIP6EventInstance));
        ESP_ERROR_CHECK(esp_event_handler_instance_unregister(WIFI_EVENT, WIFI_EVENT_NDP_CONFIRM, m_NanConfirmEventInstance));
        ESP_ERROR_CHECK(esp_event_handler_instance_unregister(WIFI_EVENT, WIFI_EVENT_NDP_INDICATION, m_NanIndicateEventInstance));
    }

    std::unique_ptr<Socket> WifiNanServer::Accept()
    {
        struct sockaddr_in6 client_addr;
        socklen_t addr_len = sizeof(client_addr);

        while (true) {
            int ssock;
            {
                std::unique_lock<std::mutex> lock(m_SocketMutex);

                while (true) {
                    if (m_Closed) {
                        ESP_LOGI(TAG, "Server socket closed, returning null");
                        return nullptr;
                    }
                    ssock = m_ServerSocket;
                    if (ssock < 0) {
                        ESP_LOGI(TAG, "Server socket not ready, waiting for IPv6 address...");
                        m_SocketAvailableCond.wait(lock);
                        continue;
                    } else {
                        break;
                    }
                }
            }

            ESP_LOGI(TAG, "Waiting for incoming connection on port %d...", m_Port);
            int sockfd;
            while (true)
            {
                sockfd = accept(ssock, (struct sockaddr*)&client_addr, &addr_len);
                if (sockfd >= 0 || errno != EAGAIN) {
                    break;
                }
            }
            if (sockfd < 0) {
                ESP_LOGE(TAG, "Failed to accept connection: %d", errno);
            }
            else {
                ESP_LOGI(TAG, "Accepted connection from %s:%d", inet6_ntoa(client_addr.sin6_addr), ntohs(client_addr.sin6_port));
                return std::make_unique<InetSocket>(&WIFI_NAN_ADAPTER, sockfd);
            }
        }
    }

    void WifiNanServer::BindServerSocket(esp_ip6_addr_t& addr)
    {
        std::lock_guard<std::mutex> lock(m_SocketMutex);

        if (m_ServerSocket >= 0) {
            ESP_LOGW(TAG, "Server socket already bound, closing and rebinding");
            CloseServerSocket();
        }

        m_ServerSocket = socket(AF_INET6, SOCK_STREAM, IPPROTO_IPV6);
        assert(m_ServerSocket >= 0);

        // spinning in a loop with timeouts seems to make connections way more reliable,
        // without a timeout it is often not possible to establish one (with Android at least)
        struct timeval timeout = { .tv_sec = 1, .tv_usec = 0 };
        setsockopt(m_ServerSocket, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));

        sockaddr_in6 server_addr{};
        server_addr.sin6_family = AF_INET6;
        server_addr.sin6_port = htons(m_Port);
        memcpy(&server_addr.sin6_addr, &addr, sizeof(server_addr.sin6_addr));

        int ret = bind(m_ServerSocket, (struct sockaddr*)&server_addr, sizeof(server_addr));
        assert(ret == 0);

        assert(listen(m_ServerSocket, ESP_WIFI_NAN_DATAPATH_MAX_PEERS) == 0);

        m_SocketAvailableCond.notify_all();
    }

    void WifiNanServer::CloseServerSocket()
    {
        std::lock_guard<std::mutex> lock(m_SocketMutex);

        if (m_ServerSocket >= 0) {
            close(m_ServerSocket);
            m_ServerSocket = -1;
        }
    }

    void WifiNanServer::OnGetIP6Event(ip_event_got_ip6_t* event_data)
    {
        if (event_data->esp_netif == esp_netif_get_handle_from_ifkey("WIFI_NAN_DEF")) {
            ESP_LOGI(TAG, "Got IPv6 address on NAN interface");
            BindServerSocket(event_data->ip6_info.ip);
        }
    }

    void WifiNanServer::GotIP6Callback(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data)
    {
        WifiNanServer* server = static_cast<WifiNanServer*>(arg);
        ip_event_got_ip6_t* got_ip6_event = static_cast<ip_event_got_ip6_t*>(event_data);
        server->OnGetIP6Event(got_ip6_event);
    }

    void WifiNanServer::NanConfirmCallback(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data)
    {
        wifi_event_ndp_confirm_t* confirm = static_cast<wifi_event_ndp_confirm_t*>(event_data);
        ESP_LOGI(TAG, "NDP Confirm Event: status=%d, ndp_id=%d", static_cast<int>(confirm->status), static_cast<int>(confirm->ndp_id));
    }

    void WifiNanServer::NanIndicateCallback(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data)
    {
        wifi_event_ndp_indication_t* indication = static_cast<wifi_event_ndp_indication_t*>(event_data);
        ESP_LOGI(TAG, "NDP indication: publish_id=%d, ndp_id=%d, peer_mac=%02x:%02x:%02x:%02x:%02x:%02x",
            static_cast<int>(indication->publish_id), static_cast<int>(indication->ndp_id),
            indication->peer_nmi[0], indication->peer_nmi[1], indication->peer_nmi[2],
            indication->peer_nmi[3], indication->peer_nmi[4], indication->peer_nmi[5]);
    }
}