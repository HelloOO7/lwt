#include <new>
#include <cstddef>
#include <cstdlib>
#include <esp_heap_caps.h>
#include <cstdio>

static bool g_IsReady = false;
thread_local int g_NewAndDeleteHeapCaps = MALLOC_CAP_DEFAULT;

void InitNewAndDelete() {
    g_IsReady = true;
}

int SetNewAndDeleteHeapCaps(int caps) {
    int oldCaps = g_NewAndDeleteHeapCaps;
    g_NewAndDeleteHeapCaps = caps;
    return oldCaps;
}

static void* Allocate(std::size_t size) {
    int caps = g_IsReady ? g_NewAndDeleteHeapCaps : MALLOC_CAP_DEFAULT;
    // ESP32 thread local storage initializes to zero
    if (caps == MALLOC_CAP_DEFAULT) {
        return malloc(size);
    }
    else {
        return heap_caps_malloc(size, caps);
    }
}

static void BadAlloc(std::size_t size) {
    fprintf(stderr, "Failed to allocate %zu bytes of memory; caps=%d free=%zu\n", size, g_NewAndDeleteHeapCaps, heap_caps_get_free_size(g_NewAndDeleteHeapCaps));
    throw std::bad_alloc();
}

void* operator new(std::size_t size) {
    void* ptr = Allocate(size);
    if (!ptr) {
        BadAlloc(size);
    }
    return ptr;
}

void* operator new[](std::size_t size) {
    return operator new(size);
}

void operator delete(void* ptr) noexcept {
    free(ptr);
}

void operator delete(void* ptr, std::size_t size) noexcept {
    free(ptr);
}

void operator delete[](void* ptr) noexcept {
    free(ptr);
}

void operator delete[](void* ptr, std::size_t size) noexcept {
    free(ptr);
}

void* operator new(std::size_t size, int caps) {
    void* ptr = heap_caps_malloc(size, caps);
    if (!ptr) {
        BadAlloc(size);
    }
    return ptr;
}

void* operator new[](std::size_t size, int caps) {
    return operator new(size, caps);
}