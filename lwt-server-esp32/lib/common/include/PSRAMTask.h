#pragma once

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_heap_caps.h"
#include <memory>

struct PSRAMTask {
    TaskHandle_t m_Handle{ nullptr };
    std::unique_ptr<StackType_t[]> m_Stack{ nullptr };
    StaticTask_t m_TaskBuffer{};
    StaticTask_t* m_TaskBufferPtr{ &m_TaskBuffer };

    inline ~PSRAMTask() {
        if (m_TaskBufferPtr != &m_TaskBuffer) {
            heap_caps_free(m_TaskBufferPtr);
            m_TaskBufferPtr = nullptr;
        }
    }

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
    if (!portVALID_TCB_MEM(pxTaskBuffer->m_TaskBufferPtr)) {
        // malloc can allocate AppMain on the PSRAM heap if it is too large, in which case we need to put this in interenal mem.
        pxTaskBuffer->m_TaskBufferPtr = (StaticTask_t*)heap_caps_malloc(sizeof(StaticTask_t), MALLOC_CAP_INTERNAL);
    }
    pxTaskBuffer->m_Handle = xTaskCreateStatic(pxTaskCode, pcName, ulStackDepth, pvParameters, uxPriority, pxTaskBuffer->m_Stack.get(), pxTaskBuffer->m_TaskBufferPtr);
    if (pxTaskBuffer->m_Handle == nullptr) {
        throw std::runtime_error("Failed to create PSRAM task");
    }
}