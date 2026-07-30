#pragma once

#include "mbedtls/md.h"
#include "mbedtls/ctr_drbg.h"
#include <vector>
#include <cstdint>
#include <array>
#include <climits>
#include <span>
#include "lwt_CryptoTypes.h"

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

        PreauthorizationTokenBlob CreatePreauthorizationToken(const std::span<const uint8_t>& activationTokenHash, int64_t expiresAt);
        VerificationResult VerifyPreauthorizationToken(const std::span<const uint8_t>& tokenBlob, const std::span<const uint8_t>& activationTokenHash, int64_t currentClock);

    private:
        HMAC HMACMessage(const std::span<const uint8_t>& message);
    };
}