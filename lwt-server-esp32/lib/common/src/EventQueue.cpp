#include "EventQueue.h"

#include <iostream>

EventQueue::EventQueue(const std::string& name, size_t capacity, size_t stackSize, int priority) :
    PubSubTask(name, stackSize, priority),
    m_Capacity(capacity)
{
}

EventQueue::~EventQueue()
{
    Close();
}

void EventQueue::Close(bool cancelPending) {
    if (cancelPending) {
        std::unique_lock lock(m_PubSubMutex);
        m_Closed = true;
        while (!m_Queue.empty()) {
            m_Queue.pop_front();
        }
    }

    PubSubTask::Close();
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

void EventQueue::ProcessData() {
    while (!m_Queue.empty()) {
        auto event = std::move(m_Queue.front());
        m_Queue.pop_front();

        event.m_Callback();
    }
}

bool EventQueue::Post(const EventCallback& event, int tag) {
    std::unique_lock lock(m_PubSubMutex);

    if (m_Closed || m_Queue.size() >= m_Capacity || EventByTagExists(tag)) {
        return false;
    }

    m_Queue.emplace_back(tag, event);
    SignalDataReady();
    return true;
}

bool EventQueue::Post(EventCallback&& event, int tag) {
    std::unique_lock lock(m_PubSubMutex);

    if (m_Closed || m_Queue.size() >= m_Capacity || EventByTagExists(tag)) {
        return false;
    }

    m_Queue.emplace_back(tag, std::move(event));
    SignalDataReady();
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