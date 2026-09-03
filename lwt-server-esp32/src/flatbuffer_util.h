#pragma once

#include "flatbuffers/flatbuffers.h"
#include <vector>
#include "PSRAMContainers.h"
#include "CommonTypes.h"

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

inline ByteVector SerializeFlatBuffer(const flatbuffers::FlatBufferBuilder& builder) {
    const uint8_t* bufferPointer = builder.GetBufferPointer();
    size_t bufferSize = builder.GetSize();
    return ByteVector(bufferPointer, bufferPointer + bufferSize);
}

flatbuffers::FlatBufferBuilder PSRAMFlatBufferBuilder(size_t initialSize = 1024);

template<typename T>
flatbuffers::Offset<flatbuffers::Vector<T>> CreateVector(flatbuffers::FlatBufferBuilder& builder, const std::span<const T>& data) {
    return builder.CreateVector(data.data(), data.size());
}
