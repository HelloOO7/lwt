#include "DigitalSignature.h"
#include "MessageDigest.h"

DigitalSignature::DigitalSignature(const ByteSpan& publicKeyPem, const ByteSpan& privateKeyPem)
{
    mbedtls_pk_init(&m_Context);
    if (!publicKeyPem.empty()) {
        assert(mbedtls_pk_parse_public_key(&m_Context, publicKeyPem.data(), publicKeyPem.size()) == 0);
    }
    if (!privateKeyPem.empty()) {
        assert(mbedtls_pk_parse_key(&m_Context, privateKeyPem.data(), privateKeyPem.size(), nullptr, 0) == 0);
    }
}

DigitalSignature::DigitalSignature(const ByteSpan& keyPem, KeyUsage keyRole) :
    DigitalSignature(keyRole == KeyUsage::VERIFY ? keyPem : ByteSpan{}, keyRole == KeyUsage::SIGN ? keyPem : ByteSpan{})
{
}

DigitalSignature::~DigitalSignature()
{
    mbedtls_pk_free(&m_Context);
}

int DigitalSignature::Sign(const ByteSpan& data, psram_vector<uint8_t>* signature, mbedtls_md_type_t hashType)
{
    psram_vector<uint8_t> digest;

    int ret = MessageDigest::Digest(data, &digest, hashType);
    if (ret != 0) {
        return ret;
    }

    return SignDigest(digest, hashType, signature);
}

int DigitalSignature::SignDigest(const ByteSpan& digest, mbedtls_md_type_t digestType, psram_vector<uint8_t>* signature)
{
    std::lock_guard lock(m_Mutex);

    size_t sigLen = 0;

    signature->resize(MBEDTLS_PK_SIGNATURE_MAX_SIZE);
    int ret = mbedtls_pk_sign(&m_Context, digestType, digest.data(), digest.size(), signature->data(), signature->size(), &sigLen);
    if (ret != 0) {
        signature->clear();
        return ret;
    }
    signature->resize(sigLen);

    return 0;
}

int DigitalSignature::Verify(const ByteSpan& data, const ByteSpan& signature, mbedtls_md_type_t hashType) {
    psram_vector<uint8_t> digest;

    int ret = MessageDigest::Digest(data, &digest, hashType);
    if (ret != 0) {
        return ret;
    }

    return VerifyDigest(digest, hashType, signature);
}

int DigitalSignature::VerifyDigest(const ByteSpan& digest, mbedtls_md_type_t digestType, const ByteSpan& signature) {
    std::lock_guard lock(m_Mutex);
    return mbedtls_pk_verify(&m_Context, digestType, digest.data(), digest.size(), signature.data(), signature.size());
}