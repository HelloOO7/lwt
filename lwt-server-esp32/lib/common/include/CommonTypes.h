#pragma once

#include <span>
#include <cstdint>
#include <array>

using ByteSpan = std::span<const uint8_t>;

using UUID = std::array<uint8_t, 16>;
