#pragma once

#include <cstdint>
#include <span>
#include <string>

inline uint32_t FNV1aHashInit() {
    return 2166136261u; // FNV offset basis
}

template<typename ByteT>
inline uint32_t FNV1aHashUpdate(uint32_t hash, ByteT byte) {
    return (hash ^ static_cast<uint8_t>(byte)) * 16777619u; // FNV prime
}

template<typename ByteT>
inline uint32_t FNV1aHashUpdate(uint32_t hash, const std::span<const ByteT>& data) {
    for (ByteT byte : data) {
        hash = FNV1aHashUpdate(hash, byte);
    }
    return hash;
}

template<typename ByteT>
inline uint32_t FNV1aHash(const std::span<const ByteT>& data) {
    uint32_t hash = FNV1aHashInit();
    for (ByteT byte : data) {
        hash = FNV1aHashUpdate(hash, static_cast<uint8_t>(byte));
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