#include "lwt_AdvData.h"

#include "BitConverter.h"

namespace lwt {

    void AdvDataBasic::set_train_line_number(const std::string& prefix, uint16_t number) {
        char ch1 = prefix.size() > 0 ? prefix[0] : 0;
        char ch2 = prefix.size() > 1 ? prefix[1] : 0;

        line_license_number = (ch1 << 17) | (ch2 << 10) | (number & 0x3FF);
    }

    void AdvDataBasic::pack(uint8_t* pDst) const {
        auto out = BitConverter<BIG_ENDIAN>::OutputStream(pDst);
        out.WriteUInt8((uint8_t)line_type);
        out.WriteUInt24(line_license_number);
        out.WriteUInt24(trip_number);
        out.WriteUInt32(direction_cis_number);
        out.WriteUInt32(stop_cis_number);
        out.WriteUInt32(stop_arrival_time | (stop_departure_time << 11) | (delay << 22));
        out.WriteUInt8(flags);
    }

    AdvDataExtended::AdvDataExtended(const AdvDataBasic& basicData) :
        AdvDataBasic(basicData)
    {
    }

    inline static size_t EncodedStringSize(const std::string& str) {
        return str.size() + 1; // +1 for null terminator
    }

    size_t AdvDataExtended::calc_size() const {
        return PACKED_SIZE + sizeof(DATA_MARK) + EncodedStringSize(cur_stop_name) + EncodedStringSize(line_name) + EncodedStringSize(headsign);
    }

    inline static void EncodeString(const std::string& str, uint8_t** ppDst) {
        std::memcpy(*ppDst, str.c_str(), str.size());
        (*ppDst)[str.size()] = '\0'; // null terminator
        *ppDst += EncodedStringSize(str);
    }

    void AdvDataExtended::pack(uint8_t* pDst) const {
        AdvDataBasic::pack(pDst);
        pDst += PACKED_SIZE;
        BitConverter<BIG_ENDIAN>::FromUInt16(DATA_MARK, pDst);
        pDst += sizeof(DATA_MARK);

        EncodeString(cur_stop_name, &pDst);
        EncodeString(line_name, &pDst);
        EncodeString(headsign, &pDst);
    }
}