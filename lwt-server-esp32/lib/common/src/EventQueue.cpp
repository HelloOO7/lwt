#include "EventQueue.h"

EventQueue::EventQueue(const std::string& name, size_t capacity, size_t stackSize, int priority) :
    m_Name(name),
    m_Capacity(capacity)
{
    xTaskCreateStaticPSRAM(TaskFunc, m_Name.c_str(), stackSize, this, priority, &m_Task);
}

EventQueue::~EventQueue()
{
    Close();
}

void EventQueue::Close(bool cancelPending) {
    std::unique_lock lock(m_Mutex);

    m_Closed = true;
    if (cancelPending) {
        while (!m_Queue.empty()) {
            m_Queue.pop_front();
        }
    }
    m_TaskReadyCV.notify_all();
    m_CloseFinishedCV.wait(lock);
    vTaskDelete(m_Task);
}

EventQueue::EventRegistration::EventRegistration(EventTag tag, EventCallback callback) :
    m_Tag(tag),
    m_Callback(std::move(callback))
{

}

EventQueue::EventRegistration::EventRegistration() :
    m_Tag(EVENT_TAG_NONE),
    m_Callback(nullptr)
{

}

void EventQueue::Run() {
    while (true) {
        EventRegistration event;

        {
            std::unique_lock lock(m_Mutex);
            while (m_Queue.empty() && !m_Closed) {
                m_TaskReadyCV.wait(lock);
            }

            if (m_Queue.empty()) { // closed
                break;
            }

            event = std::move(m_Queue.front());
            m_Queue.pop_front();
        }

        event.m_Callback();
    }

    if (m_Closed) {
        m_CloseFinishedCV.notify_all();
    }
}

bool EventQueue::Post(const EventCallback& event, int tag) {
    std::unique_lock lock(m_Mutex);

    if (m_Closed || m_Queue.size() >= m_Capacity || EventByTagExists(tag)) {
        return false;
    }

    m_Queue.emplace_back(tag, event);
    m_TaskReadyCV.notify_one();
    return true;
}

bool EventQueue::Post(EventCallback&& event, int tag) {
    std::unique_lock lock(m_Mutex);

    if (m_Closed || m_Queue.size() >= m_Capacity || EventByTagExists(tag)) {
        return false;
    }

    m_Queue.emplace_back(tag, std::move(event));
    m_TaskReadyCV.notify_one();
    return true;
}

bool EventQueue::EventByTagExists(int tag) {
    if (tag == EVENT_TAG_NONE) {
        return false;
    }

    for (auto&& registration : m_Queue) {
        if (registration.m_Tag == tag) {
            return true;
        }
    }
    return false;
}

void EventQueue::TaskFunc(void* param) {
    static_cast<EventQueue*>(param)->Run();
}