#pragma once

#include <cstdint>
#include <memory>
#include "esp_heap_caps.h"

template <typename T>
class psram_allocator : public std::allocator<T> {
public:
    using value_type = T;
    using pointer = T*;
    using const_pointer = const T*;
    using reference = T&;
    using const_reference = const T&;
    using size_type = std::size_t;
    using difference_type = std::ptrdiff_t;

    psram_allocator() noexcept {}

    template <typename U>
    psram_allocator(const psram_allocator<U>&) noexcept {}

    pointer allocate(size_type n) {
        if (n > std::numeric_limits<size_type>::max() / sizeof(T)) {
            throw std::bad_alloc();
        }

        pointer p = static_cast<pointer>(heap_caps_malloc(n * sizeof(T), MALLOC_CAP_SPIRAM));
        if (!p) throw std::bad_alloc();

        return p;
    }

    void deallocate(pointer p, size_type) noexcept {
        heap_caps_free(p);
    }

    template <typename U>
    struct rebind {
        using other = psram_allocator<U>;
    };
};