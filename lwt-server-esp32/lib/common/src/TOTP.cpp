#include "TOTP.h"
#include "BitConverter.h"

#include <algorithm>
#include <array>
#include <cstdio>
#include <ctime>
#include <stdexcept>
#include "HMAC.h"

using BC = BitConverter<std::endian::big>;

TOTP::TOTP(const ByteSpan& secret, mbedtls_md_type_t digestType, uint8_t digits, int64_t refreshPeriod) :
    m_Secret(secret.begin(), secret.end()),
    m_RefreshPeriod(refreshPeriod),
    m_DigestType(digestType),
    m_Digits(digits),
    m_DigitsModulus(1)
{
    if (m_Digits < MIN_DIGITS || m_Digits > MAX_DIGITS) {
        throw std::invalid_argument("TOTP digits must be between 6 and 8");
    }
    for (uint8_t digit = 0; digit < m_Digits; ++digit) {
        m_DigitsModulus *= 10;
    }
    if (m_DigestType != MBEDTLS_MD_SHA1 &&
        m_DigestType != MBEDTLS_MD_SHA256 &&
        m_DigestType != MBEDTLS_MD_SHA512) {
        throw std::invalid_argument("TOTP digest must be SHA-1, SHA-256 or SHA-512");
    }
    if (mbedtls_md_info_from_type(m_DigestType) == nullptr) {
        throw std::invalid_argument("TOTP digest is not available");
    }
}

uint32_t TOTP::Generate(int64_t unixTime) const {
    uint64_t counter = unixTime / m_RefreshPeriod;
    std::array<uint8_t, sizeof(counter)> counterBytes{};
    BC::FromUInt64(counter, counterBytes.data());

    ByteVector digest = HMACForDigestType(m_DigestType, m_Secret, counterBytes);

    const size_t offset = digest.back() & 0x0F;
    const uint32_t binaryCode = BC::ToUInt32(&digest[offset]) & 0x7FFFFFFF;
    return binaryCode % m_DigitsModulus;
}

std::string TOTP::GenerateString(int64_t unixTime) const {
    char code[MAX_DIGITS + 1];
    std::snprintf(code, sizeof(code), "%0*lu", m_Digits, Generate(unixTime));
    return code;
}
