#include "lwt_PreauthorizationTokenManager.h"
#include <cassert>
#include "BitConverter.h"
#include "psa/crypto.h"
#include "lwt_SecureTokenFormat.h"

namespace lwt {

    static constexpr uint16_t TOKEN_BLOB_VERSION = 1;

    PreauthorizationTokenManager::PreauthorizationTokenManager(HMACSHA256& hmac)
        : m_HMAC(hmac)
    {

    }

    PreauthorizationTokenManager::~PreauthorizationTokenManager()
    {

    }

    using BitConverter = ::BitConverter<std::endian::native>;

    PreauthorizationTokenBlob PreauthorizationTokenManager::CreatePreauthorizationToken(const ByteSpan& activationTokenHash, int64_t expiresAt)
    {
        PreauthorizationTokenBlob data(SecureToken::CalcHmacTokenSize(m_HMAC, sizeof(uint16_t) + sizeof(uint8_t) + activationTokenHash.size() + sizeof(int64_t)));

        BitConverter::OutputStream out(data.data());
        out.WriteUInt16(TOKEN_BLOB_VERSION);
        out.WriteUInt8(activationTokenHash.size());
        out.WriteBytes(activationTokenHash);
        out.WriteInt64(expiresAt);

        SecureToken::AddHmac(m_HMAC, data);

        return data;
    }

    PreauthorizationTokenManager::VerificationResult PreauthorizationTokenManager::VerifyPreauthorizationToken(const ByteSpan& tokenBlob, const ByteSpan& activationTokenHash, int64_t currentClock)
    {
        if (tokenBlob.size() < SecureToken::CalcHmacTokenSize(m_HMAC, sizeof(uint16_t) + sizeof(uint8_t))) {
            return VerificationResult::TOKEN_CORRUPTED;
        }

        if (!SecureToken::VerifyHmac(m_HMAC, tokenBlob)) {
            return VerificationResult::INVALID_HMAC;
        }

        BitConverter::InputStream in(tokenBlob.data());

        uint16_t version = in.ReadUInt16();
        if (version != TOKEN_BLOB_VERSION) {
            return VerificationResult::VERSION_MISMATCH;
        }

        uint8_t activationTokenHashSize = in.ReadUInt8();
        if (activationTokenHashSize != activationTokenHash.size()) {
            return VerificationResult::TOKEN_MISMATCH;
        }
        auto activationTokenHashFromBlob = in.ReadBytes(activationTokenHashSize);
        if (!std::equal(activationTokenHash.begin(), activationTokenHash.end(), activationTokenHashFromBlob.begin())) {
            return VerificationResult::TOKEN_MISMATCH;
        }

        int64_t expiresAt = in.ReadInt64();
        if (currentClock > expiresAt) {
            return VerificationResult::TOKEN_EXPIRED;
        }

        return VerificationResult::OK;
    }

    PreauthorizationTokenManager::HMACHash PreauthorizationTokenManager::HMACMessage(const ByteSpan& message)
    {
        return m_HMAC.Compute(message);
    }
}