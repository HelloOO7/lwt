#pragma once

#include <vector>
#include <cstdint>
#include <array>
#include <climits>
#include <span>
#include "lwt_CryptoTypes.h"
#include "lwt_CommonTypes.h"
#include "psa/crypto.h"
#include "PSRAMContainers.h"

namespace lwt {

    using PreauthorizationTokenBlob = psram_vector<uint8_t>;

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
        psa_key_id_t m_HMACKeyId{ PSA_KEY_ID_NULL };

    public:
        PreauthorizationTokenManager(const std::vector<uint8_t>& hmacKey);
        ~PreauthorizationTokenManager();

        PreauthorizationTokenBlob CreatePreauthorizationToken(const ByteSpan& activationTokenHash, int64_t expiresAt);
        VerificationResult VerifyPreauthorizationToken(const ByteSpan& tokenBlob, const ByteSpan& activationTokenHash, int64_t currentClock);

    private:
        HMAC HMACMessage(const ByteSpan& message);
    };
}