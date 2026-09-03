#pragma once

#include <vector>
#include <cstdint>
#include <array>
#include <climits>
#include <span>
#include "CryptoTypes.h"
#include "CommonTypes.h"
#include "psa/crypto.h"
#include "PSRAMContainers.h"
#include "HMAC.h"

namespace lwt {

    using PreauthorizationTokenBlob = ByteVector;

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
        using HMACHash = SHA256Hash;

    private:
        HMACSHA256& m_HMAC;

    public:
        PreauthorizationTokenManager(HMACSHA256& hmac);
        ~PreauthorizationTokenManager();

        PreauthorizationTokenBlob CreatePreauthorizationToken(const ByteSpan& activationTokenHash, int64_t expiresAt);
        VerificationResult VerifyPreauthorizationToken(const ByteSpan& tokenBlob, const ByteSpan& activationTokenHash, int64_t currentClock);

    private:
        HMACHash HMACMessage(const ByteSpan& message);
    };
}