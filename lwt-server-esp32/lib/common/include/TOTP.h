#pragma once

#include "CommonTypes.h"
#include "mbedtls/md.h"

#include <cstdint>
#include <string>
#include <vector>
#include "HMAC.h"

class TOTP {
private:
    ByteVector m_Secret;
    uint32_t m_RefreshPeriod;
    mbedtls_md_type_t m_DigestType;
    uint8_t m_Digits;
    uint32_t m_DigitsModulus;
public:
    static constexpr int64_t DEFAULT_REFRESH_PERIOD = 30000;
    static constexpr uint8_t MIN_DIGITS = 6;
    static constexpr uint8_t MAX_DIGITS = 8;
    static constexpr uint8_t DEFAULT_DIGITS = MIN_DIGITS;

    // interface adapter from java library
    TOTP(const ByteSpan& secret,
        mbedtls_md_type_t digestType = MBEDTLS_MD_SHA256,
        uint8_t digits = DEFAULT_DIGITS,
        int64_t refreshPeriod = DEFAULT_REFRESH_PERIOD);

    uint32_t Generate(int64_t unixTime) const;
    std::string GenerateString(int64_t unixTime) const;
};