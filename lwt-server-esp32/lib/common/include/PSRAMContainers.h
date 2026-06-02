#pragma once

#include "PSRAMAllocator.h"
#include <vector>
#include <string>

template <typename T>
using psram_vector = std::vector<T, psram_allocator<T>>;

using psram_string = std::basic_string<char, std::char_traits<char>, psram_allocator<char>>;