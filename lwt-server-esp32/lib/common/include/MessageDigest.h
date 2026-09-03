#pragma once

#include "mbedtls/md.h"
#include "CommonTypes.h"
#include "CryptoTypes.h"
#include <vector>

class MessageDigest {
public:
    template<typename TAllocator = std::allocator<uint8_t>>
    static int Digest(const ByteSpan& data, std::vector<uint8_t, TAllocator>* digest, mbedtls_md_type_t hashType = MBEDTLS_MD_SHA256) {
        auto mdInfo = mbedtls_md_info_from_type(hashType);
        size_t digestSize = mbedtls_md_get_size(mdInfo);
        digest->resize(digestSize);

        return Digest(data, *digest, hashType);
    }

    static int Digest(const ByteSpan& data, const WritableByteSpan& digest, mbedtls_md_type_t hashType = MBEDTLS_MD_SHA256);

    static SHA256Hash SHA256(const ByteSpan& data);
};