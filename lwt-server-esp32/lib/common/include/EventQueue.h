#pragma once

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"
#include <functional>
#include <deque>
#include <mutex>
#include <condition_variable>
#include <string>
#include "PSRAMContainers.h"

class EventQueue
{
public:
    using EventTag = int;
    static constexpr EventTag EVENT_TAG_NONE = -1;
    using EventCallback = std::function<void()>;

    static constexpr size_t STACK_PSRAM_BIT = 0x80000000;
    static constexpr size_t STACK_SIZE_MASK = ~STACK_PSRAM_BIT;

private:
    struct EventRegistration {
        EventTag m_Tag;
        EventCallback m_Callback;

        EventRegistration(EventTag tag, EventCallback callback);
        EventRegistration();
    };

private:
    std::string m_Name;
    size_t m_Capacity;

    TaskHandle_t m_Task;
    StaticTask_t m_TaskBuffer;
    psram_vector<StackType_t> m_TaskStackPSRAM;
    
    std::deque<EventRegistration> m_Queue;
    std::mutex m_Mutex;
    std::condition_variable m_TaskReadyCV;
    std::condition_variable m_CloseFinishedCV;

    bool m_Closed{ false };
public:
    EventQueue(const std::string& name, size_t capacity, size_t stackSize = 4096, size_t priority = tskIDLE_PRIORITY + 1);
    ~EventQueue();

    /**
     * @brief Clear all pending events, wait for any running event to finish, and stop the task loop.
     * This may only be called once. After closing a queue, Post does nothing.
     */
    void Close(bool cancelPending = false);

    bool Post(const EventCallback& event, int tag = EVENT_TAG_NONE);
    bool Post(EventCallback&& event, int tag = EVENT_TAG_NONE);

private:
    void Run();
    static void TaskFunc(void* param);
    bool EventByTagExists(int tag);
};