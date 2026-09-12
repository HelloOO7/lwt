#include "EventQueue.h"

PubSubTask::PubSubTask(const std::string& name, size_t stackSize, int priority) :
    m_Name(name)
{
    xTaskCreateStaticPSRAM(TaskFunc, m_Name.c_str(), stackSize, this, priority, &m_Task);
}

PubSubTask::~PubSubTask()
{
    Close();
}

void PubSubTask::Close() {
    std::unique_lock lock(m_PubSubMutex);

    m_Closed = true;
    m_DataReady = false;
    m_DataReadyCV.notify_all();
    m_CloseFinishedCV.wait(lock);
}

void PubSubTask::Run() {
    while (true) {
        std::unique_lock lock(m_PubSubMutex);
        m_DataReadyCV.wait(lock, [this] { return m_DataReady || m_Closed; });

        if (m_Closed) {
            break;
        }

        ProcessData();
        m_DataReady = false;
    }

    if (m_Closed) {
        m_CloseFinishedCV.notify_all();
    }

    vTaskDelete(nullptr);
}

void PubSubTask::SignalDataReady() {
    m_DataReady = true;
    m_DataReadyCV.notify_all();
}

void PubSubTask::TaskFunc(void* param) {
    static_cast<PubSubTask*>(param)->Run();
}