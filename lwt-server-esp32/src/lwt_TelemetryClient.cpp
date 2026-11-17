#include "lwt_TelemetryClient.h"
#include "SystemTime.h"
#include "lwdn_BleLink.h"
#include "esp_mac.h"
#include "netcheck.h"

namespace lwt {

    TelemetryClient::TelemetryClient(MOSClient& mosClient, int syncTaskPriority) :
        EventSenderTask("TelemetryClient", 6144, syncTaskPriority),
        m_MOSClient(mosClient)
    {
        auto mac = lwdn::BLE_ADAPTER.GetLinkAddress();
        char macStr[strlen(MACSTR) + 1];
        snprintf(macStr, sizeof(macStr), MACSTR, MAC2STR(mac));
        m_SenderId = macStr;
    }

    void TelemetryClient::LogEvent(MOSTelemetryEventType type) {
        LogEvent(type, std::nullopt, std::monostate{});
    }

    void TelemetryClient::LogEvent(MOSTelemetryEventType type, const std::string& parameter) {
        LogEvent(type, psram_string(parameter));
    }

    void TelemetryClient::LogEvent(MOSTelemetryEventType type, const psram_string& parameter) {
        LogEvent(type, parameter, std::monostate{});
    }

    void TelemetryClient::LogEvent(MOSTelemetryEventType type, const std::optional<psram_string>& parameter, const std::variant<std::monostate, psram_string, psram_json>& extraData) {
        MOSTelemetryEvent event{
            .EventClass = MOSTelemetryEventClass::LWT,
            .SenderId = m_SenderId,
            .LocalTimestamp = SystemTime::UptimeMillis(),
            .AbsoluteTimestamp = SystemTime::IsSynchronized() ? std::optional{ OffsetDateTime::now() } : std::nullopt,
            .EventType = type,
            .EventParameter = parameter,
            .ExtraData = extraData
        };
        EnqueuePushEvent(std::move(event));
    }

    void TelemetryClient::EnqueuePushEvent(MOSTelemetryEvent&& event) {
        std::unique_lock lock(m_PubSubMutex);
        while (m_EventBuffer.size() >= EVENT_BUFFER_LIMIT) {
            m_EventBuffer.pop_front();
        }
        m_EventBuffer.push_back(std::move(event));
        SignalDataReady();
    }

    bool TelemetryClient::SendEventsToServer() {
        if (!m_EventBuffer.empty()) {
            std::vector<MOSTelemetryEvent> eventsVector{ std::make_move_iterator(m_EventBuffer.begin()), std::make_move_iterator(m_EventBuffer.end()) };

            MOSTelemetryEventBatch eventBatch{
                .Events = eventsVector,
                .CurrentLocalTimestamp = SystemTime::UptimeMillis()
            };
            int statusCode = m_MOSClient.TelemetryPushEvents(eventBatch);
            if (!MOSClient::IsStatusOK(statusCode)) {
                // move data back, retry later
                m_EventBuffer = { std::make_move_iterator(eventsVector.begin()), std::make_move_iterator(eventsVector.end()) };
                return false;
            }
            m_EventBuffer.clear();
        }
        return true;
    }
}