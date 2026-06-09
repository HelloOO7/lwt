#pragma once

#include <cstdint>

namespace lwtp {

    enum class ProtocolVersion : uint16_t {
        FOUNDATION = 1,
        FIRST_NOT_SUPPORTED
    };

    enum class ControlCommand {
        INVALID = 0,
        GO_AHEAD = 220,
        START_TLS = 250,
        COMMAND_NOT_IMPLEMENTED = 502,
        BAD_SEQUENCE_OF_COMMANDS = 503
    };

    static constexpr size_t MAX_PACKET_SIZE = 8192;

    struct PacketHeader {
        static constexpr const char* MAGIC = "LWTP";

        static constexpr uint8_t FLAG_CONTROL_MESSAGE = 0x01;

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