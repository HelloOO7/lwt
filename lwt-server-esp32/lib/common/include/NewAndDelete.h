#pragma once

#include <cstddef>
#include <new>

/**
 * @brief Initializes the new and delete operators to use the ESP32 heap capabilities.
 * This must be called inside a task, i.e. NOT a static initializer.
 */
void InitNewAndDelete();
/**
 * @brief Set ESP32 heap capabilities for new and delete operators in this thread.
 *
 * @param caps heap caps
 * @return int old heap caps
 */
int SetNewAndDeleteHeapCaps(int caps);

void* operator new(std::size_t size);
void* operator new[](std::size_t size);
void operator delete(void* ptr) noexcept;
void operator delete(void* ptr, std::size_t size) noexcept;
void operator delete[](void* ptr) noexcept;
void operator delete[](void* ptr, std::size_t size) noexcept;

template<int CAPS>
class UseHeapCaps {
private:
    int m_OldCaps;
public:
    UseHeapCaps() {
        m_OldCaps = SetNewAndDeleteHeapCaps(CAPS);
    }

    ~UseHeapCaps() {
        SetNewAndDeleteHeapCaps(m_OldCaps);
    }
};