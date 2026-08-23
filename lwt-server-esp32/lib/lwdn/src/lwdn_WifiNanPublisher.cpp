#include "lwdn_WifiNanPublisher.h"

namespace lwdn {

    static constexpr const char* TAG = "WifiNanPublisher";

    WifiNanPublisher::WifiNanPublisher(const Config& config) :
        m_Config{ config }
    {
        strcpy(m_PublishConfig.service_name, m_Config.ServiceName.c_str());
        m_PublishConfig.type = m_Config.ServiceType;

        size_t mfPos = 0;
        for (auto&& mf : config.MatchingFilters) {
            if (mfPos != 0) {
                m_PublishConfig.matching_filter[mfPos] = ',';
                ++mfPos;
            }
            if (mfPos + mf.size() >= ESP_WIFI_MAX_FILTER_LEN) {
                ESP_LOGE(TAG, "Matching filter data exceeds maximum allowed size of %d bytes", ESP_WIFI_MAX_FILTER_LEN);
                break;
            }
            std::copy(mf.begin(), mf.end(), m_PublishConfig.matching_filter + mfPos);
            mfPos += mf.size();
        }
        m_PublishConfig.matching_filter[mfPos] = '\0';

        m_PublishConfig.single_replied_event = m_Config.SingleRepliedEvent;
        m_PublishConfig.ssi = m_Config.SSI.data();
        m_PublishConfig.ssi_len = m_Config.SSI.size();

        m_PublishConfig.datapath_reqd = IsFeatureEnabled(Feature::DATAPATH);
        m_PublishConfig.fsd_reqd = IsFeatureEnabled(Feature::FSD);
        m_PublishConfig.fsd_gas = IsFeatureEnabled(Feature::FSD_GAS);
        m_PublishConfig.ndp_resp_needed = !m_Config.AcceptAllNDPs;

        ESP_LOGI(TAG, "Publisher initialized with service name: %s, matching filter: %s, type: %d, single_replied_event: %d, datapath_reqd: %d, fsd_reqd: %d, fsd_gas: %d",
            m_PublishConfig.service_name,
            m_PublishConfig.matching_filter,
            m_PublishConfig.type,
            m_PublishConfig.single_replied_event,
            m_PublishConfig.datapath_reqd,
            m_PublishConfig.fsd_reqd,
            m_PublishConfig.fsd_gas);
    }

    bool WifiNanPublisher::IsFeatureEnabled(Feature feature) const
    {
        return (m_Config.Features & feature) == feature;
    }

    void WifiNanPublisher::Publish()
    {
        std::lock_guard lock(m_ConfigLock);
        if (m_ServiceId != 0) {
            return;
        }

        m_ServiceId = esp_wifi_nan_publish_service(&m_PublishConfig);
        if (m_ServiceId == 0) {
            ESP_LOGE(TAG, "Error starting NAN publish");
            return;
        }
        else {
            ESP_ERROR_CHECK(esp_event_handler_instance_register(WIFI_EVENT, WIFI_EVENT_NAN_RECEIVE, &WifiNanPublisher::NanReceiveCallback, this, &m_NanReceiveEventInstance));
        }
    }

    void WifiNanPublisher::Cancel()
    {
        if (m_ServiceId != 0) {
            esp_err_t err = esp_wifi_nan_cancel_service(m_ServiceId);
            if (err != 0) {
                ESP_LOGE(TAG, "Error cancelling NAN publish; err=%d", err);
            }
            m_ServiceId = 0;
            ESP_ERROR_CHECK(esp_event_handler_instance_unregister(WIFI_EVENT, WIFI_EVENT_NAN_RECEIVE, &m_NanReceiveEventInstance));
        }
    }

    bool WifiNanPublisher::IsPublishing() const
    {
        return m_ServiceId != 0;
    }

    void WifiNanPublisher::UpdateSSI(const ByteSpan& ssi)
    {
        std::lock_guard lock(m_ConfigLock);

        ESP_LOGW(TAG, "Warning: updating SSI on the fly is not yet supported, changes will be reflected only after restarting the publisher");

        m_Config.SSI.assign(ssi.begin(), ssi.end());
        m_PublishConfig.ssi = m_Config.SSI.data();
        m_PublishConfig.ssi_len = m_Config.SSI.size();
    }

    void WifiNanPublisher::RegisterMessageHandler(MessageHandler&& handler)
    {
        std::lock_guard lock(m_ConfigLock);

        m_MessageHandlers.push_back(std::move(handler));
    }

    void WifiNanPublisher::HandleReceiveEvent(wifi_event_nan_receive_t* event)
    {
        std::lock_guard lock(m_ConfigLock);

        Message message(*this, ByteSpan(event->ssi, event->ssi_len), event);

        for (auto&& handler : m_MessageHandlers) {
            handler(message);

            if (message.WasReplySent()) {
                break;
            }
        }

        if (!message.WasReplySent()) {
            ESP_LOGW(TAG, "No handler found for the received message, sending empty response");
            message.Reply({});
        }
    }

    void WifiNanPublisher::NanReceiveCallback(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data)
    {
        auto event = static_cast<wifi_event_nan_receive_t*>(event_data);

        ESP_LOGI(TAG, "Received NAN event: inst_id=%d, peer_inst_id=%d, peer_if_mac=%02x:%02x:%02x:%02x:%02x:%02x, ssi_len=%d",
            event->inst_id,
            event->peer_inst_id,
            event->peer_if_mac[0], event->peer_if_mac[1], event->peer_if_mac[2],
            event->peer_if_mac[3], event->peer_if_mac[4], event->peer_if_mac[5],
            event->ssi_len);

        WifiNanPublisher* publisher = static_cast<WifiNanPublisher*>(arg);
        if (publisher) {
            if (event->inst_id == publisher->m_ServiceId) {
                publisher->HandleReceiveEvent(event);
            }
        }
    }

    WifiNanPublisher::Message::Message(WifiNanPublisher& pub, const ByteSpan& data, wifi_event_nan_receive_t* recvEvent) :
        ByteSpan(data),
        m_Publisher(pub),
        m_ReceiveEvent(recvEvent)
    {
    }

    bool WifiNanPublisher::Message::WasReplySent() const
    {
        return m_ReplySent;
    }

    ByteSpan WifiNanPublisher::Message::GetCurrentPublisherSSI()
    {
        return m_Publisher.m_Config.SSI;
    }

    void WifiNanPublisher::Message::Reply(const ByteSpan& data)
    {
        if (WasReplySent()) {
            ESP_LOGW(TAG, "Warning: Reply has already been sent for this message, ignoring subsequent reply");
            return;
        }
        m_ReplySent = true;

        wifi_nan_followup_params_t fup{};
        fup.inst_id = m_ReceiveEvent->inst_id;
        fup.peer_inst_id = m_ReceiveEvent->peer_inst_id;
        memcpy(fup.peer_mac, m_ReceiveEvent->peer_if_mac, sizeof(fup.peer_mac));
        fup.ssi_len = data.size();
        fup.ssi = const_cast<uint8_t*>(data.data());
        assert(fup.vendor_ie == nullptr);
        
        esp_err_t err = esp_wifi_nan_send_message(&fup);
        if (err != ESP_OK) {
            ESP_LOGE(TAG, "Error sending NAN follow-up message; err=%d", err);
        }
    }
}