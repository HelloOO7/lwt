#include "DigitalSignature.h"
#include "MessageDigest.h"

void DigitalSignatureImpl::Init(mbedtls_pk_context* context)
{
    m_Context = context;
    mbedtls_pk_init(m_Context);
}

void DigitalSignatureImpl::Init(mbedtls_pk_context* context, const ByteSpan& publicKeyPem, const ByteSpan& privateKeyPem)
{
    Init(context);
    if (!publicKeyPem.empty()) {
        assert(mbedtls_pk_parse_public_key(context, publicKeyPem.data(), publicKeyPem.size()) == 0);
    }
    if (!privateKeyPem.empty()) {
        assert(mbedtls_pk_parse_key(context, privateKeyPem.data(), privateKeyPem.size(), nullptr, 0) == 0);
    }
}

void DigitalSignatureImpl::Init(mbedtls_pk_context* context, const ByteSpan& keyPem, KeyUsage keyRole) {
    Init(context, keyRole == KeyUsage::VERIFY ? keyPem : ByteSpan{}, keyRole == KeyUsage::SIGN ? keyPem : ByteSpan{});
}

void DigitalSignatureImpl::Free()
{
    if (m_Context) {
        mbedtls_pk_free(m_Context);
        m_Context = nullptr;
    }
}

int DigitalSignatureImpl::Sign(const ByteSpan& data, ByteVector* signature, mbedtls_md_type_t hashType)
{
    ByteVector digest;

    int ret = MessageDigest::Digest(data, &digest, hashType);
    if (ret != 0) {
        return ret;
    }

    return SignDigest(digest, hashType, signature);
}

int DigitalSignatureImpl::SignDigest(const ByteSpan& digest, mbedtls_md_type_t digestType, ByteVector* signature)
{
    std::lock_guard lock(m_Mutex);

    size_t sigLen = 0;

    signature->resize(MBEDTLS_PK_SIGNATURE_MAX_SIZE);
    int ret = mbedtls_pk_sign(m_Context, digestType, digest.data(), digest.size(), signature->data(), signature->size(), &sigLen);
    if (ret != 0) {
        signature->clear();
        return ret;
    }
    signature->resize(sigLen);

    return 0;
}

int DigitalSignatureImpl::Verify(const ByteSpan& data, const ByteSpan& signature, mbedtls_md_type_t hashType) {
    ByteVector digest;

    int ret = MessageDigest::Digest(data, &digest, hashType);
    if (ret != 0) {
        return ret;
    }

    return VerifyDigest(digest, hashType, signature);
}

int DigitalSignatureImpl::VerifyDigest(const ByteSpan& digest, mbedtls_md_type_t digestType, const ByteSpan& signature) {
    std::lock_guard lock(m_Mutex);
    return mbedtls_pk_verify(m_Context, digestType, digest.data(), digest.size(), signature.data(), signature.size());
}

DigitalSignature::DigitalSignature(const ByteSpan& publicKeyPem, const ByteSpan& privateKeyPem)
{
    Init(&m_Context, publicKeyPem, privateKeyPem);
}

DigitalSignature::DigitalSignature(const ByteSpan& keyPem, KeyUsage keyRole)
{
    Init(&m_Context, keyPem, keyRole);
}

DigitalSignature::~DigitalSignature()
{
    Free();
}