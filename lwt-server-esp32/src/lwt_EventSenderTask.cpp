#include "lwt_EventSenderTask.h"
#include "netcheck.h"
#include "esp_log.h"

namespace lwt {

    static constexpr const char* TAG = "EventSenderTask";

    EventSenderTask::EventSenderTask(const std::string& taskName, size_t stackSize, int syncTaskPriority) :
        PubSubTask(taskName, stackSize, syncTaskPriority),
        m_RetryTimer(
            [this]() {
                std::unique_lock lock(m_PubSubMutex);
                ESP_LOGI(TAG, "Retry timer triggered, signaling data ready");
                SignalDataReady();
            },
            0, TimerProc::Type::ONESHOT
        )
    {
    }

    void EventSenderTask::ProcessData() {
        if (!netcheck_is_connected() || !SendEventsToServer()) {
            ESP_LOGW(TAG, "Failed to send events to server, scheduling retry");
            m_RetryTimer.RestartIn(30 * 1000 * 1000); // retry in 30 seconds
        }
        else {
            m_RetryTimer.Stop();
        }
    }
}