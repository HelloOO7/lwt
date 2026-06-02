#pragma once

#include <cstdint>

namespace lwtp {

    enum class ProtocolVersion : uint16_t {
        FOUNDATION = 1,
        FIRST_NOT_SUPPORTED
    };

    static constexpr size_t MAX_PACKET_SIZE = 8192;

    struct PacketHeader {
        static constexpr const char* MAGIC = "LWTP";

        char m_Magic[4];
        uint16_t m_Version;
        uint8_t  m_Flags;
        uint8_t  m_HeaderSize;
        uint16_t m_PayloadSize;
    };

    static_assert(sizeof(PacketHeader) == 10, "PacketHeader must be 10 bytes");

    inline void SwapByteOrder(uint16_t& value) {
        value = (value >> 8) | (value << 8);
    }

    inline void SwapByteOrder(PacketHeader& header) {
        SwapByteOrder(header.m_Version);
        SwapByteOrder(header.m_PayloadSize);
    }
}