#include "lwt_PreauthorizationTokenManager.h"
#include <cassert>
#include "BitConverter.h"
#include "psa/crypto.h"

namespace lwt {

    static constexpr uint16_t TOKEN_BLOB_VERSION = 1;

    PreauthorizationTokenManager::PreauthorizationTokenManager(const std::vector<uint8_t>& hmacKey)
        : m_HMACKey(hmacKey)
    {
        psa_key_attributes_t keyAttributes = PSA_KEY_ATTRIBUTES_INIT;
        psa_set_key_usage_flags(&keyAttributes, PSA_KEY_USAGE_SIGN_MESSAGE | PSA_KEY_USAGE_VERIFY_MESSAGE);
        psa_set_key_algorithm(&keyAttributes, PSA_ALG_HMAC(PSA_ALG_SHA_256));
        psa_set_key_type(&keyAttributes, PSA_KEY_TYPE_HMAC);
        assert(psa_import_key(&keyAttributes, hmacKey.data(), hmacKey.size(), &m_HMACKeyId) == PSA_SUCCESS);
    }

    PreauthorizationTokenManager::~PreauthorizationTokenManager()
    {
        if (m_HMACKeyId != PSA_KEY_ID_NULL) {
            psa_destroy_key(m_HMACKeyId);
        }
    }

    using BitConverter = ::BitConverter<std::endian::native>;

    PreauthorizationTokenBlob PreauthorizationTokenManager::CreatePreauthorizationToken(const ByteSpan& activationTokenHash, int64_t expiresAt)
    {
        PreauthorizationTokenBlob data(sizeof(uint16_t) + sizeof(uint8_t) + activationTokenHash.size() + sizeof(int64_t) + HMAC{}.size());

        BitConverter::OutputStream out(data.data());
        out.WriteUInt16(TOKEN_BLOB_VERSION);
        out.WriteUInt8(activationTokenHash.size());
        out.WriteBytes(activationTokenHash);
        out.WriteInt64(expiresAt);

        auto hmac = HMACMessage({ data.data(), data.size() - HMAC{}.size() });
        out.WriteBytes(hmac);

        return data;
    }

    PreauthorizationTokenManager::VerificationResult PreauthorizationTokenManager::VerifyPreauthorizationToken(const ByteSpan& tokenBlob, const ByteSpan& activationTokenHash, int64_t currentClock)
    {
        if (tokenBlob.size() < sizeof(uint8_t) + HMAC{}.size()) {
            return VerificationResult::TOKEN_CORRUPTED;
        }

        ByteSpan innerData(tokenBlob.data(), tokenBlob.size() - HMAC{}.size());
        ByteSpan hmac(tokenBlob.data() + innerData.size(), HMAC{}.size());

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

    PreauthorizationTokenManager::HMAC PreauthorizationTokenManager::HMACMessage(const ByteSpan& message)
    {
        HMAC hmacOutput;
        size_t macLength = 0;
        assert(psa_mac_compute(m_HMACKeyId, PSA_ALG_HMAC(PSA_ALG_SHA_256), message.data(), message.size(), hmacOutput.data(), hmacOutput.size(), &macLength) == PSA_SUCCESS);
        return hmacOutput;
    }
}