#include <new>
#include <cstddef>
#include <cstdlib>
#include <esp_heap_caps.h>
#include <cstdio>

thread_local int g_NewAndDeleteHeapCaps = MALLOC_CAP_DEFAULT;

int SetNewAndDeleteHeapCaps(int caps) {
    int oldCaps = g_NewAndDeleteHeapCaps;
    g_NewAndDeleteHeapCaps = caps;
    return oldCaps;
}

void* Allocate(std::size_t size) {
    int caps = g_NewAndDeleteHeapCaps;
    // ESP32 thread local storage initializes to zero
    if (caps == MALLOC_CAP_DEFAULT || caps == 0) {
        return malloc(size);
    }
    else {
        return heap_caps_malloc(size, caps);
    }
}

void* operator new(std::size_t size) {
    void* ptr = Allocate(size);
    if (!ptr) {
        throw std::bad_alloc();
    }
    return ptr;
}

void* operator new[](std::size_t size) {
    void* ptr = Allocate(size);
    if (!ptr) {
        throw std::bad_alloc();
    }
    return ptr;
}

void operator delete(void* ptr) noexcept {
    free(ptr);
}

void operator delete[](void* ptr) noexcept {
    free(ptr);
}