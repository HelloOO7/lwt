#include "HMAC.h"

template<typename THMAC>
ByteVector HMACForKey(const ByteSpan& key, const ByteSpan& message) {
    auto hmac = THMAC::ImportKey(key);
    ByteVector hash(THMAC::KEY_SIZE);
    hmac.Compute(message, WritableByteSpan(hash));
    return hash;
}

ByteVector HMACForDigestType(mbedtls_md_type_t digestType, const ByteSpan& key, const ByteSpan& message) {
    switch (digestType) {
    case MBEDTLS_MD_SHA1:
        return HMACForKey<HMACSHA1>(key, message);
    case MBEDTLS_MD_SHA256:
        return HMACForKey<HMACSHA256>(key, message);
    case MBEDTLS_MD_SHA512:
        return HMACForKey<HMACSHA512>(key, message);
    default:
        throw std::invalid_argument("Unsupported digest type for HMAC");
    }
}