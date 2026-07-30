#pragma once

#include <array>
#include <cstdint>
#include <span>

using SHA256Hash = std::array<uint8_t, 32>;
using SHA256HashView = std::span<const uint8_t, 32>;