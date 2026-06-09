#pragma once

#include <cstdint>

extern uint8_t TLS_LWT_SERVER_KEY_DEBUG_START[] asm("_binary_LWT_Server_debug_key_key_start");
extern uint8_t TLS_LWT_SERVER_KEY_DEBUG_END[] asm("_binary_LWT_Server_debug_key_key_end");

extern uint8_t TLS_DEVICE_CRT_START[] asm("_binary_esp32_alice_crt_start");
extern uint8_t TLS_DEVICE_CRT_END[] asm("_binary_esp32_alice_crt_end");