#pragma once

#include <cstddef>
#include <new>

int SetNewAndDeleteHeapCaps(int caps);

void* operator new(std::size_t size);
void* operator new[](std::size_t size);
void operator delete(void* ptr) noexcept;
void operator delete[](void* ptr) noexcept;

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