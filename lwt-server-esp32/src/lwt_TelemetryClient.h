#pragma once

#include "PSRAMTask.h"
#include "lwt_MosClient.h"
#include <mutex>
#include <condition_variable>
#include "esp_event.h"
#include "lwt_EventSenderTask.h"
#include <optional>

namespace lwt {

    class TelemetryClient : protected EventSenderTask
    {
    private:
        static constexpr size_t EVENT_BUFFER_LIMIT = 100;

    private:
        MOSClient& m_MOSClient;

        psram_string m_SenderId;
        psram_deque<MOSTelemetryEvent> m_EventBuffer;

    public:
        TelemetryClient(MOSClient& mosClient, int syncTaskPriority);

        void LogEvent(MOSTelemetryEventType type);
        void LogEvent(MOSTelemetryEventType type, const std::string& parameter);
        void LogEvent(MOSTelemetryEventType type, const psram_string& parameter);
        void LogEvent(
            MOSTelemetryEventType type,
            const std::optional<psram_string>& parameter,
            const std::variant<std::monostate, psram_string, psram_json>& extraData
        );

        virtual bool SendEventsToServer() override;

    private:
        void EnqueuePushEvent(MOSTelemetryEvent&& event);
    };
}