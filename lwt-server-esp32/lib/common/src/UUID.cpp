#include "UUID.h"
#include "BitConverter.h"
#include "psa/crypto.h"
#include <sys/time.h>
#include <stdexcept>
#include <cctype>

using BC = BitConverter<std::endian::big>;

UUID UUID::Nil() {
    return UUID{};
}

UUID UUID::V4() {
    UUID uuid;
    psa_generate_random(uuid.data(), uuid.size());
    uuid.SetVersion(4);
    uuid.SetVariant(2);
    return uuid;
}

uint64_t GetCurrentUnixTimeMillis() {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return static_cast<uint64_t>(tv.tv_sec) * 1000 + static_cast<uint64_t>(tv.tv_usec) / 1000;
}

UUID UUID::V7() {
    UUID uuid;
    BC::FromInt64(GetCurrentUnixTimeMillis(), &uuid[0]);
    psa_generate_random(&uuid[6], 10);
    uuid.SetVersion(7);
    uuid.SetVariant(2);
    return uuid;
}

uint8_t ParseNibble(char c) {
    c = std::tolower(c);
    if (c >= '0' && c <= '9') {
        return c - '0';
    }
    else if (c >= 'a' && c <= 'f') {
        return 10 + (c - 'a');
    }
    else {
        throw std::invalid_argument("Invalid hex character");
    }
}

uint8_t ParseByte(const std::string_view& str, size_t index) {
    return (ParseNibble(str[index]) << 4) | ParseNibble(str[index + 1]);
}

UUID UUID::Parse(const std::string_view& str) {
    if (str.size() != 36) {
        throw std::invalid_argument("Invalid UUID string length");
    }

    UUID uuid;
    std::fill(uuid.begin(), uuid.end(), 0);

    for (size_t i = 0, bi = 0; i < str.size();) {
        if (str[i] == '-') {
            ++i;
            continue;
        }

        if (bi >= 16 || i + 2 > str.size()) {
            throw std::invalid_argument("Invalid UUID string format");
        }

        uuid[bi] = ParseByte(str, i);
        i += 2;
    }

    return uuid;
}

uint8_t UUID::GetVersion() const {
    return BC::GetBits(at(6), 1, 4);
}

uint8_t UUID::GetVariant() const {
    return BC::GetBits(at(8), 6, 2);
}

static constexpr bool BYTE_DASH_LUT[16]{
    false, false, false, false,
    true, false,
    true, false,
    true, false,
    true, false, false, false, false, false
};

std::string UUID::ToString() const {
    std::string out;
    for (size_t i = 0; i < size(); ++i) {
        if (BYTE_DASH_LUT[i]) {
            out += '-';
        }
        char buffer[3];
        snprintf(buffer, sizeof(buffer), "%02x", at(i));
        out += buffer;
    }
    return out;
}

void UUID::SetVersion(uint8_t version) {
    BC::SetBits(at(6), 1, 4, version);
}

void UUID::SetVariant(uint8_t variant) {
    BC::SetBits(at(8), 6, 2, variant);
}
