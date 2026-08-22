#pragma once

#include <cstdint>
#include <cstring>
#include <string>
#include "trip_information_generated.h"

namespace lwt {

    struct AdvDataBasic {
        static constexpr uint8_t PACKED_SIZE = 20;

        static constexpr uint8_t FLAG_IS_AT_STOP = (1 << 0);
        static constexpr uint8_t FLAG_CAN_USE_TICKETING = (1 << 1);

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

        void pack(uint8_t* pDst) const;
    };

    struct AdvDataExtended : public AdvDataBasic {
        static constexpr uint16_t DATA_MARK = 0x4544; // 'ED'

        std::string cur_stop_name;
        std::string line_name;
        std::string headsign;

        AdvDataExtended(const AdvDataBasic& basicData);

        size_t calc_size() const;

        void pack(uint8_t* pDst) const;
    };
}