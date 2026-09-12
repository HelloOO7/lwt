#pragma once

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <functional>
#include <deque>
#include <mutex>
#include <condition_variable>
#include <string>
#include "PSRAMContainers.h"
#include "PSRAMTask.h"

class PubSubTask
{
public:
    static constexpr int DEFAULT_TASK_PRIORITY = tskIDLE_PRIORITY + 1;

protected:
    std::mutex m_PubSubMutex;
    bool m_Closed{ false };

public:
    std::string m_Name;

    PSRAMTask m_Task;

    bool m_DataReady{ false };
    std::condition_variable m_DataReadyCV;
    std::condition_variable m_CloseFinishedCV;

public:
    PubSubTask(const std::string& name, size_t stackSize = 4096, int priority = DEFAULT_TASK_PRIORITY);
    ~PubSubTask();

    virtual void ProcessData() = 0;
    void SignalDataReady();

    void Close();

private:
    void Run();
    static void TaskFunc(void* param);
};