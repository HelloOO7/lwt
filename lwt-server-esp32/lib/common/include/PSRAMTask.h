#pragma once

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_heap_caps.h"
#include <memory>

struct PSRAMTask {
    TaskHandle_t m_Handle{ nullptr };
    std::unique_ptr<StackType_t[]> m_Stack{ nullptr };
    StaticTask_t m_TaskBuffer{};

    inline operator TaskHandle_t() const {
        return m_Handle;
    }
};

inline void xTaskCreateStaticPSRAM(TaskFunction_t pxTaskCode,
    const char* const pcName,
    const uint32_t ulStackDepth,
    void* const pvParameters,
    UBaseType_t uxPriority,
    PSRAMTask* pxTaskBuffer) {

    pxTaskBuffer->m_Stack = std::unique_ptr<StackType_t[]>(static_cast<StackType_t*>(heap_caps_aligned_alloc(16, ulStackDepth, MALLOC_CAP_SPIRAM)));
    if (pxTaskBuffer->m_Stack == nullptr) {
        throw std::bad_alloc();
    }
    pxTaskBuffer->m_Handle = xTaskCreateStatic(pxTaskCode, pcName, ulStackDepth, pvParameters, uxPriority, pxTaskBuffer->m_Stack.get(), &pxTaskBuffer->m_TaskBuffer);
    if (pxTaskBuffer->m_Handle == nullptr) {
        throw std::runtime_error("Failed to create PSRAM task");
    }
}