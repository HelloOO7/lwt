#pragma once

#include "esp_nan.h"
#include <string>
#include <cstdint>
#include <vector>
#include "PSRAMContainers.h"
#include "EnumBitflags.h"
#include "esp_event.h"
#include <functional>
#include <mutex>
#include <span>
#include "CommonTypes.h"

namespace lwdn {

    class WifiNanPublisher {
    public:
        enum class Feature {
            DATAPATH,
            FSD,
            FSD_GAS
        };

        struct Config {
            std::string ServiceName;
            wifi_nan_service_type_t ServiceType{ NAN_PUBLISH_UNSOLICITED };
            std::vector<ByteVector> MatchingFilters;
            Feature Features{ };
            bool SingleRepliedEvent{ true };
            bool AcceptAllNDPs{ true };
            ByteVector SSI;
        };

        class Message : public ByteSpan {
            friend class WifiNanPublisher;
        private:
            WifiNanPublisher& m_Publisher;
            wifi_event_nan_receive_t* m_ReceiveEvent;
            bool m_ReplySent{ false };

        private:
            Message(WifiNanPublisher& pub, const ByteSpan& data, wifi_event_nan_receive_t* recvEvent);

            bool WasReplySent() const;

        public:
            ByteSpan GetCurrentPublisherSSI();

            void Reply(const ByteSpan& data);
        };

        using MessageHandler = std::function<void(Message&)>;

    private:
        Config m_Config;
        std::mutex m_ConfigLock;
        wifi_nan_publish_cfg_t m_PublishConfig{};
        uint8_t m_ServiceId{ 0 };

        std::vector<MessageHandler> m_MessageHandlers;

        esp_event_handler_instance_t m_NanReceiveEventInstance;

    public:
        WifiNanPublisher(const Config& config);

        bool IsFeatureEnabled(Feature feature) const;

        void Publish();
        void Cancel();
        bool IsPublishing() const;

        void UpdateSSI(const ByteSpan& ssi);

        void RegisterMessageHandler(MessageHandler&& handler);

    private:
        void HandleReceiveEvent(wifi_event_nan_receive_t* event);
        static void NanReceiveCallback(void* arg, esp_event_base_t event_base, int32_t event_id, void* event_data);
    };

    DEFINE_ENUM_FLAG_OPERATORS(WifiNanPublisher::Feature);
}