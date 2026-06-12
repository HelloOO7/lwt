#include "lwt_AdvDataLegacy.h"

#include "BitConverter.h"

namespace lwt {

    void AdvDataLegacy::set_train_line_number(const std::string& prefix, uint16_t number) {
        char ch1 = prefix.size() > 0 ? prefix[0] : 0;
        char ch2 = prefix.size() > 1 ? prefix[1] : 0;

        line_license_number = (ch1 << 17) | (ch2 << 10) | (number & 0x3FF);
    }

    void AdvDataLegacy::pack(uint8_t* pDst) {
        auto out = BitConverter<BIG_ENDIAN>::OutputStream(pDst);
        out.WriteUInt8((uint8_t)line_type);
        out.WriteUInt24(line_license_number);
        out.WriteUInt24(trip_number);
        out.WriteUInt32(direction_cis_number);
        out.WriteUInt32(stop_cis_number);
        out.WriteUInt32(stop_arrival_time | (stop_departure_time << 11) | (delay << 22));
        out.WriteUInt8(flags);
    }
}