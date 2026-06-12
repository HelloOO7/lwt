#pragma once

#include <cstdint>
#include <cstring>
#include "publisher_ssi_generated.h"

namespace lwt {

    struct AdvDataLegacy {
        static constexpr uint8_t PACKED_SIZE = 20;

        static constexpr uint8_t FLAG_IS_AT_STOP = 0x1;

        LineType line_type;
        uint32_t line_license_number;
        uint32_t trip_number;

        uint32_t direction_cis_number;

        uint32_t stop_cis_number;
        uint16_t stop_arrival_time;
        uint16_t stop_departure_time;

        int16_t delay;
        uint8_t flags;

        void set_train_line_number(const std::string& prefix, uint16_t number);

        void pack(uint8_t* pDst);
    };
}