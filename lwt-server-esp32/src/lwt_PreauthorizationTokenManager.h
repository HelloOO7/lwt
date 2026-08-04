#pragma once

#include "mbedtls/md.h"
#include "mbedtls/ctr_drbg.h"
#include <vector>
#include <cstdint>
#include <array>
#include <climits>
#include <span>
#include "lwt_CryptoTypes.h"
#include "lwt_CommonTypes.h"

namespace lwt {

    using PreauthorizationTokenBlob = std::vector<uint8_t>;

    class PreauthorizationTokenManager {
    public:
        enum class VerificationResult {
            OK,
            TOKEN_CORRUPTED,
            INVALID_HMAC,
            VERSION_MISMATCH,
            TOKEN_MISMATCH,
            TOKEN_EXPIRED
        };

    private:
        using HMAC = SHA256Hash;

    private:
        std::vector<uint8_t> m_HMACKey;
        mbedtls_md_context_t m_HMACCtx;

    public:
        PreauthorizationTokenManager(const std::vector<uint8_t>& hmacKey);

        PreauthorizationTokenBlob CreatePreauthorizationToken(const ByteSpan& activationTokenHash, int64_t expiresAt);
        VerificationResult VerifyPreauthorizationToken(const ByteSpan& tokenBlob, const ByteSpan& activationTokenHash, int64_t currentClock);

    private:
        HMAC HMACMessage(const ByteSpan& message);
    };
}