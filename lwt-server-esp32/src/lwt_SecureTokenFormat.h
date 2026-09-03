#pragma once

#include "CommonTypes.h"
#include "HMAC.h"
#include "DigitalSignature.h"
#include "Certificate.h"
#include <stdexcept>

class SecureToken {
public:
    template<psa_algorithm_t Alg, typename THash>
    static size_t CalcHmacTokenSize(size_t innerDataSize) {
        return innerDataSize + THash{}.size();
    }

    template<psa_algorithm_t Alg, typename THash>
    static size_t CalcHmacTokenSize(HMAC<Alg, THash>& hmac, size_t innerDataSize) {
        return innerDataSize + THash{}.size();
    }

    template<psa_algorithm_t Alg, typename THash>
    static void AddHmac(HMAC<Alg, THash>& hmac, const WritableByteSpan& preallocatedData) {
        if (preallocatedData.size() < THash{}.size()) {
            throw std::invalid_argument("Preallocated data size is too small for HMAC");
        }

        ByteSpan innerData(preallocatedData.data(), preallocatedData.size() - THash{}.size());
        WritableByteSpan hmacValue(preallocatedData.data() + innerData.size(), THash{}.size());

        assert(hmac.Compute(innerData, hmacValue) == PSA_SUCCESS);
    }

    template<psa_algorithm_t Alg, typename THash>
    static bool VerifyHmac(HMAC<Alg, THash>& hmac, const ByteSpan& token) {
        if (token.size() < THash{}.size()) {
            return false;
        }

        ByteSpan innerData(token.data(), token.size() - THash{}.size());
        ByteSpan hmacValue(token.data() + innerData.size(), THash{}.size());

        return hmac.Verify(innerData, hmacValue);
    }

    using KeyInfo = uint32_t;

    static ByteVector CreateSignedToken(DigitalSignature& privateKey, const ByteSpan& message, KeyInfo keyInfo = 0);
    static ByteVector CreateSignedToken(DigitalSignature& privateKey, ByteVector&& message, KeyInfo keyInfo = 0);

    static bool ParseSignedToken(const ByteSpan& token, ByteSpan* pData, ByteSpan* pSignature, KeyInfo* pKeyInfo = nullptr);
    static bool VerifySignedToken(DigitalSignature& publicKey, const ByteSpan& token);
    static bool VerifySignedToken(Certificate& publicKeyCert, const ByteSpan& token);
};