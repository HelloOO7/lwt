#pragma once

#include "psa/crypto.h"
#include "CryptoTypes.h"

template<psa_algorithm_t Alg, typename THash>
class HMAC {
public:
    static constexpr size_t KEY_SIZE = THash{}.size();

private:
    psa_key_id_t m_KeyId{ PSA_KEY_ID_NULL };
    bool m_IsKeyImported{ false };

public:
    HMAC(psa_key_id_t keyId, bool isKeyImported = false) :
        m_KeyId(keyId),
        m_IsKeyImported(isKeyImported)
    {

    }

    HMAC(HMAC&& other) noexcept {
        *this = std::move(other);
    }

    ~HMAC() {
        if (m_IsKeyImported && m_KeyId != PSA_KEY_ID_NULL) {
            psa_destroy_key(m_KeyId);
        }
    }

    HMAC& operator=(HMAC&& other) noexcept {
        if (this != &other) {
            std::swap(m_KeyId, other.m_KeyId);
            std::swap(m_IsKeyImported, other.m_IsKeyImported);
        }
        return *this;
    }

    static HMAC<Alg, THash> ImportKey(const ByteSpan& keyData) {
        psa_key_attributes_t keyAttributes = PSA_KEY_ATTRIBUTES_INIT;
        psa_set_key_usage_flags(&keyAttributes, PSA_KEY_USAGE_SIGN_MESSAGE | PSA_KEY_USAGE_VERIFY_MESSAGE);
        psa_set_key_algorithm(&keyAttributes, Alg);
        psa_set_key_type(&keyAttributes, PSA_KEY_TYPE_HMAC);

        psa_key_id_t keyId;
        assert(psa_import_key(&keyAttributes, keyData.data(), keyData.size(), &keyId) == PSA_SUCCESS);

        return HMAC<Alg, THash>(keyId, true);
    }

    int Compute(const ByteSpan& message, const WritableByteSpan& hash) const {
        size_t hashLen = 0;
        return psa_mac_compute(m_KeyId, Alg, message.data(), message.size(), hash.data(), hash.size(), &hashLen);
    }

    void Compute(const ByteSpan& message, THash& hash) const {
        assert(Compute(message, WritableByteSpan(hash)) == PSA_SUCCESS);
    }

    THash Compute(const ByteSpan& message) const {
        THash hash;
        Compute(message, hash);
        return hash;
    }

    bool Verify(const ByteSpan& message, const ByteSpan& expectedHash) const {
        auto actualHash = Compute(message);
        return std::equal(actualHash.begin(), actualHash.end(), expectedHash.begin(), expectedHash.end());
    }

    bool Verify(const ByteSpan& message, const THash& expectedHash) const {
        return Verify(message, ByteSpan(expectedHash));
    }
};

using HMACSHA256 = HMAC<PSA_ALG_HMAC(PSA_ALG_SHA_256), SHA256Hash>;