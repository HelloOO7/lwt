#include "lwt_PreauthorizationTokenManager.h"
#include <cassert>
#include "BitConverter.h"

namespace lwt {

    static constexpr uint16_t TOKEN_BLOB_VERSION = 1;

    PreauthorizationTokenManager::PreauthorizationTokenManager(const std::vector<uint8_t>& hmacKey)
        : m_HMACKey(hmacKey)
    {
        mbedtls_md_init(&m_HMACCtx);
        int err = mbedtls_md_setup(&m_HMACCtx, mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), true);
        assert(err == 0);
    }

    using BitConverter = ::BitConverter<std::endian::native>;

    PreauthorizationTokenBlob PreauthorizationTokenManager::CreatePreauthorizationToken(const std::span<const uint8_t>& activationTokenHash, int64_t expiresAt)
    {
        PreauthorizationTokenBlob data(sizeof(uint16_t) + sizeof(uint8_t) + activationTokenHash.size() + sizeof(int64_t) + HMAC{}.size());

        BitConverter::OutputStream out(data.data());
        out.WriteUInt16(TOKEN_BLOB_VERSION);
        out.WriteUInt8(activationTokenHash.size());
        out.WriteBytes(activationTokenHash);
        out.WriteInt64(expiresAt);

        auto hmac = HMACMessage({data.data(), data.size() - HMAC{}.size()});
        out.WriteBytes(hmac);

        return data;
    }

    PreauthorizationTokenManager::VerificationResult PreauthorizationTokenManager::VerifyPreauthorizationToken(const std::span<const uint8_t>& tokenBlob, const std::span<const uint8_t>& activationTokenHash, int64_t currentClock)
    {
        if (tokenBlob.size() < sizeof(uint8_t) + HMAC{}.size()) {
            return VerificationResult::TOKEN_CORRUPTED;
        }

        std::span<const uint8_t> innerData(tokenBlob.data(), tokenBlob.size() - HMAC{}.size());
        std::span<const uint8_t> hmac(tokenBlob.data() + innerData.size(), HMAC{}.size());

        HMAC expectedHMAC = HMACMessage(innerData);
        if (!std::equal(hmac.begin(), hmac.end(), expectedHMAC.begin())) {
            return VerificationResult::INVALID_HMAC;
        }

        BitConverter::InputStream in(innerData.data());
        
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

    PreauthorizationTokenManager::HMAC PreauthorizationTokenManager::HMACMessage(const std::span<const uint8_t>& message)
    {
        int err = mbedtls_md_hmac_starts(&m_HMACCtx, m_HMACKey.data(), m_HMACKey.size());
        assert(err == 0);

        err = mbedtls_md_hmac_update(&m_HMACCtx, message.data(), message.size());
        assert(err == 0);

        HMAC hmacOutput;
        err = mbedtls_md_hmac_finish(&m_HMACCtx, hmacOutput.data());
        assert(err == 0);

        return hmacOutput;
    }
}