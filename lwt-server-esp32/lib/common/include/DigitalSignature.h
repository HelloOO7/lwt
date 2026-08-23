#pragma once

#include "mbedtls/pk.h"
#include "PSRAMContainers.h"
#include "CommonTypes.h"
#include <mutex>

class DigitalSignature {
public:
    enum class KeyUsage {
        VERIFY,
        SIGN
    };

private:
    std::mutex m_Mutex;
    mbedtls_pk_context m_Context;

public:
    DigitalSignature(const ByteSpan& publicKeyPem, const ByteSpan& privateKeyPem);
    DigitalSignature(const ByteSpan& keyPem, KeyUsage keyRole);
    ~DigitalSignature();

    int Sign(const ByteSpan& data, psram_vector<uint8_t>* signature, mbedtls_md_type_t hashType = MBEDTLS_MD_SHA256);
    int SignDigest(const ByteSpan& digest, mbedtls_md_type_t digestType, psram_vector<uint8_t>* signature);
    
    int Verify(const ByteSpan& data, const ByteSpan& signature, mbedtls_md_type_t hashType = MBEDTLS_MD_SHA256);
    int VerifyDigest(const ByteSpan& digest, mbedtls_md_type_t digestType, const ByteSpan& signature);
};