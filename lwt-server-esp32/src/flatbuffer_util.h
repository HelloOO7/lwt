#pragma once

#include "flatbuffers/flatbuffers.h"
#include <vector>
#include "PSRAMContainers.h"

template<typename T>
const T* GetAndVerify(const void* buffer, size_t bufferSize) {
    const T* result = flatbuffers::GetRoot<const T>(buffer);
    flatbuffers::Verifier verifier(static_cast<const uint8_t*>(buffer), bufferSize);
    if (!verifier.VerifyBuffer<T>()) {
        return nullptr;
    }
    return result;
}

template<typename T, typename TContainer>
const T* GetAndVerify(const TContainer& buffer) {
    return GetAndVerify<T>(buffer.data(), buffer.size());
}

inline psram_vector<uint8_t> SerializeFlatBuffer(const flatbuffers::FlatBufferBuilder& builder) {
    const uint8_t* bufferPointer = builder.GetBufferPointer();
    size_t bufferSize = builder.GetSize();
    return psram_vector<uint8_t>(bufferPointer, bufferPointer + bufferSize);
}

flatbuffers::FlatBufferBuilder PSRAMFlatBufferBuilder(size_t initialSize = 1024);