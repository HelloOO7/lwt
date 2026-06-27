#pragma once

#include "machine/endian.h"
#include <cstring>

template<int TOrder>
class BitConverter {
public:
    static uint8_t ToUInt8(const uint8_t* bytes) {
        return *bytes;
    }

    static int8_t ToInt8(const uint8_t* bytes) {
        return (int8_t)(*bytes);
    }

    static uint16_t ToUInt16(const uint8_t* bytes) {
        return ConvertTo<uint16_t>(bytes);
    }

    static int16_t ToInt16(const uint8_t* bytes) {
        return ConvertTo<int16_t>(bytes);
    }

    static uint32_t ToUInt24(const uint8_t* bytes) {
        return ConvertTo<uint32_t>(bytes, 3);
    }

    static int32_t ToInt24(const uint8_t* bytes) {
        return ConvertTo<int32_t>(bytes, 3);
    }

    static uint32_t ToUInt32(const uint8_t* bytes) {
        return ConvertTo<uint32_t>(bytes);
    }

    static int32_t ToInt32(const uint8_t* bytes) {
        return ConvertTo<int32_t>(bytes);
    }

    static uint64_t ToUInt64(const uint8_t* bytes) {
        return ConvertTo<uint64_t>(bytes);
    }

    static int64_t ToInt64(const uint8_t* bytes) {
        return ConvertTo<int64_t>(bytes);
    }

    static void FromUInt8(uint8_t value, uint8_t* bytes) {
        *bytes = value;
    }

    static void FromInt8(int8_t value, uint8_t* bytes) {
        *bytes = (uint8_t)value;
    }

    static void FromUInt16(uint16_t value, uint8_t* bytes) {
        ConvertFrom<uint16_t>(value, bytes);
    }

    static void FromInt16(int16_t value, uint8_t* bytes) {
        ConvertFrom<int16_t>(value, bytes);
    }

    static void FromUInt24(uint32_t value, uint8_t* bytes) {
        ConvertFrom<uint32_t>(value, bytes, 3);
    }

    static void FromInt24(int32_t value, uint8_t* bytes) {
        ConvertFrom<int32_t>(value, bytes, 3);
    }

    static void FromUInt32(uint32_t value, uint8_t* bytes) {
        ConvertFrom<uint32_t>(value, bytes);
    }

    static void FromInt32(int32_t value, uint8_t* bytes) {
        ConvertFrom<int32_t>(value, bytes);
    }

    static void FromUInt64(uint64_t value, uint8_t* bytes) {
        ConvertFrom<uint64_t>(value, bytes);
    }

    static void FromInt64(int64_t value, uint8_t* bytes) {
        ConvertFrom<int64_t>(value, bytes);
    }

private:
    template<typename T>
    static T ConvertTo(const uint8_t* bytes, size_t size = sizeof(T)) {
        T value = 0;
        if constexpr (TOrder == BIG_ENDIAN) {
            for (size_t i = 0; i < size; ++i) {
                value |= static_cast<T>(bytes[i]) << ((size - 1 - i) * 8);
            }
        }
        else {
            std::memcpy(&value, bytes, size);
        }
        return value;
    }

    template<typename T>
    static void ConvertFrom(T value, uint8_t* bytes, size_t size = sizeof(T)) {
        if constexpr (TOrder == BIG_ENDIAN) {
            for (size_t i = 0; i < size; ++i) {
                bytes[i] = (value >> ((size - 1 - i) * 8)) & 0xFF;
            }
        }
        else {
            std::memcpy(bytes, &value, size);
        }
    }

public:
    class InputStream {
    private:
        const uint8_t* m_Data;
    public:
        InputStream(const uint8_t* data) : m_Data(data) {}

        uint8_t ReadUInt8() {
            return Read<uint8_t>();
        }

        int8_t ReadInt8() {
            return Read<int8_t>();
        }

        uint16_t ReadUInt16() {
            return Read<uint16_t>();
        }

        int16_t ReadInt16() {
            return Read<int16_t>();
        }

        uint32_t ReadUInt24() {
            return Read<uint32_t>(3);
        }

        int32_t ReadInt24() {
            return Read<int32_t>(3);
        }

        uint32_t ReadUInt32() {
            return Read<uint32_t>();
        }

        int32_t ReadInt32() {
            return Read<int32_t>();
        }

        uint64_t ReadUInt64() {
            return Read<uint64_t>();
        }

        int64_t ReadInt64() {
            return Read<int64_t>();
        }

    private:
        template<typename T>
        T Read(size_t size = sizeof(T)) {
            T value = ConvertTo<T>(m_Data, size);
            m_Data += size;
            return value;
        }
    };

    class OutputStream {
    private:
        uint8_t* m_Data;
    public:
        OutputStream(uint8_t* data) : m_Data(data) {}

        void WriteUInt8(uint8_t value) {
            Write<uint8_t>(value);
        }

        void WriteInt8(int8_t value) {
            Write<int8_t>(value);
        }

        void WriteUInt16(uint16_t value) {
            Write<uint16_t>(value);
        }

        void WriteInt16(int16_t value) {
            Write<int16_t>(value);
        }

        void WriteUInt24(uint32_t value) {
            Write<uint32_t>(value, 3);
        }

        void WriteInt24(int32_t value) {
            Write<int32_t>(value, 3);
        }

        void WriteUInt32(uint32_t value) {
            Write<uint32_t>(value);
        }

        void WriteInt32(int32_t value) {
            Write<int32_t>(value);
        }

        void WriteUInt64(uint64_t value) {
            Write<uint64_t>(value);
        }

        void WriteInt64(int64_t value) {
            Write<int64_t>(value);
        }

    private:
        template<typename T>
        void Write(T value, size_t size = sizeof(T)) {
            ConvertFrom<T>(value, m_Data, size);
            m_Data += size;
        }
    };
};