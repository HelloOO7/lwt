#pragma once

#include <cstdint>
#include <span>
#include <string>

template<typename ByteT>
inline uint32_t FNV1aHash(const std::span<const ByteT>& data) {
    constexpr uint32_t FNV_OFFSET_BASIS = 2166136261u;
    constexpr uint32_t FNV_PRIME = 16777619u;

    uint32_t hash = FNV_OFFSET_BASIS;
    for (ByteT byte : data) {
        hash ^= static_cast<uint8_t>(byte);
        hash *= FNV_PRIME;
    }
    return hash;
}

inline uint32_t FNV1aHash(const std::span<const uint8_t>& data) {
    return FNV1aHash<uint8_t>(data);
}

inline uint32_t FNV1aHash(const std::span<const char>& data) {
    return FNV1aHash<char>(data);
}

template<typename Traits, typename Allocator>
inline uint32_t FNV1aHash(const std::basic_string<Traits, Allocator>& data) {
    return FNV1aHash<char>(std::span<char>(data.data(), data.size()));
}