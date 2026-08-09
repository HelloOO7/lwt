#pragma once

#include "esp_nan.h"
#include "esp_event.h"
#include "esp_wifi.h"
#include "lwdn_WifiNanPublisher.h"
#include "lwdn_ServerSocket.h"
#include "lwdn_WifiNanLink.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include <sys/types.h>
#include <mutex>
#include <condition_variable>

namespace lwdn {

    class WifiNanServer : public ServerSocket
    {
    private:
        WifiNanPublisher& m_Publisher;
        in_port_t m_Port;

        int m_ServerSocket{ -1 };
        bool m_Closed{ false };
        std::mutex m_SocketMutex;
        std::condition_variable m_SocketAvailableCond;

        esp_event_handler_instance_t m_GetIP6EventInstance;
        esp_event_handler_instance_t m_NanIndicateEventInstance;
        esp_event_handler_instance_t m_NanConfirmEventInstance;

    public:
        WifiNanServer(WifiNanPublisher& publisher, in_port_t port);
        ~WifiNanServer();

        virtual std::unique_ptr<Socket> Accept() override;

        virtual LinkAdapter* GetLinkAdapter() const override { return &WIFI_NAN_ADAPTER; }

    private:
        void BindServerSocket(esp_ip6_addr_t& addr);
        void CloseServerSocket();

        void OnGetIP6Event(ip_event_got_ip6_t* event_data);

        static void GotIP6Callback(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data);
        static void NanConfirmCallback(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data);
        static void NanIndicateCallback(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data);
    };
}
