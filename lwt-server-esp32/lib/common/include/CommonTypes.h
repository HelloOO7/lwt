#pragma once

#include <span>
#include <cstdint>
#include <array>
#include <vector>
#include "PSRAMAllocator.h"

using ByteSpan = std::span<const uint8_t>;
using WritableByteSpan = std::span<uint8_t>;
using ByteVector = std::vector<uint8_t, psram_allocator<uint8_t>>;