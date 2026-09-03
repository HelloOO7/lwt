#include "MessageDigest.h"

#include <cassert>
#include "psa/crypto_values.h"

int MessageDigest::Digest(const ByteSpan& data, const WritableByteSpan& digest, mbedtls_md_type_t hashType) {
    auto mdInfo = mbedtls_md_info_from_type(hashType);
    size_t digestSize = mbedtls_md_get_size(mdInfo);
    if (digest.size() < digestSize) {
        return MBEDTLS_ERR_MD_ALLOC_FAILED;
    }

    int ret = mbedtls_md(mdInfo, data.data(), data.size(), digest.data());
    if (ret != 0) {
        return ret;
    }

    return 0;
}

SHA256Hash MessageDigest::SHA256(const ByteSpan& data) {
    SHA256Hash hash;
    int ret = Digest(data, hash, MBEDTLS_MD_SHA256);
    assert(ret == 0); // This should never fail for SHA256
    return hash;
}