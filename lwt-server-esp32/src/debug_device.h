#pragma once

#include <cstdint>

enum class DebugDeviceID {
    NONE,
    ALICE,
    BOB
};

DebugDeviceID get_debug_device_id();

uint8_t* get_debug_device_crt_start();
uint8_t* get_debug_device_crt_end();