#pragma once

#include "PSRAMTask.h"
#include <mutex>
#include <condition_variable>
#include "esp_event.h"
#include "PubSubTask.h"
#include "TimerProc.h"
#include <optional>

namespace lwt {

    class EventSenderTask : protected PubSubTask
    {
    private:
        TimerProc m_RetryTimer;

    public:
        EventSenderTask(const std::string& taskName, size_t stackSize, int syncTaskPriority);

        virtual void ProcessData() override;

        virtual bool SendEventsToServer() = 0;
    };
}