#pragma once

#include "mbedtls/pk.h"
#include "PSRAMContainers.h"
#include "CommonTypes.h"
#include <mutex>

class DigitalSignatureImpl {
public:
    enum class KeyUsage {
        VERIFY,
        SIGN
    };

private:
    std::mutex m_Mutex;
    mbedtls_pk_context* m_Context;

public:
    void Init(mbedtls_pk_context* context);
    void Init(mbedtls_pk_context* context, const ByteSpan& publicKeyPem, const ByteSpan& privateKeyPem);
    void Init(mbedtls_pk_context* context, const ByteSpan& keyPem, KeyUsage keyRole);
    void Free();

public:
    int Sign(const ByteSpan& data, ByteVector* signature, mbedtls_md_type_t hashType = MBEDTLS_MD_SHA256);
    int SignDigest(const ByteSpan& digest, mbedtls_md_type_t digestType, ByteVector* signature);
    
    int Verify(const ByteSpan& data, const ByteSpan& signature, mbedtls_md_type_t hashType = MBEDTLS_MD_SHA256);
    int VerifyDigest(const ByteSpan& digest, mbedtls_md_type_t digestType, const ByteSpan& signature);
};

class DigitalSignature : public DigitalSignatureImpl {
public:
    using KeyUsage = DigitalSignatureImpl::KeyUsage;
private:
    mbedtls_pk_context m_Context;

public:
    DigitalSignature(const ByteSpan& publicKeyPem, const ByteSpan& privateKeyPem);
    DigitalSignature(const ByteSpan& keyPem, KeyUsage keyRole);
    ~DigitalSignature();
};