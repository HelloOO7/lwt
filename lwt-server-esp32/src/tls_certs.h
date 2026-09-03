#pragma once

#include <cstdint>

extern uint8_t TLS_LWT_SERVER_KEY_DEBUG_START[] asm("_binary_LWT_Server_debug_key_key_start");
extern uint8_t TLS_LWT_SERVER_KEY_DEBUG_END[] asm("_binary_LWT_Server_debug_key_key_end");

extern uint8_t TLS_DEVICE_CRT_A_START[] asm("_binary_esp32_alice_crt_start");
extern uint8_t TLS_DEVICE_CRT_A_END[] asm("_binary_esp32_alice_crt_end");

extern uint8_t TLS_DEVICE_CRT_B_START[] asm("_binary_esp32_bob_crt_start");
extern uint8_t TLS_DEVICE_CRT_B_END[] asm("_binary_esp32_bob_crt_end");

extern uint8_t TLS_ROOT_CRT_START[] asm("_binary_ROPID_Root_CA_Certificate__DEBUG__crt_start");
extern uint8_t TLS_ROOT_CRT_END[] asm("_binary_ROPID_Root_CA_Certificate__DEBUG__crt_end");
