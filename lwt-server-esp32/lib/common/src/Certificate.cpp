#include "Certificate.h"
#include <stdexcept>

Certificate::Certificate(const ByteSpan& certPemOrDer) {
    mbedtls_x509_crt_init(&m_Cert);
    int ret = mbedtls_x509_crt_parse(&m_Cert, certPemOrDer.data(), certPemOrDer.size());
    if (ret != 0) {
        throw std::invalid_argument("Failed to parse certificate");
    }
    m_Signature.Init(&m_Cert.pk);
}

Certificate::~Certificate() {
    // do NOT call m_Signature.Free() here, as it does not own the context
    mbedtls_x509_crt_free(&m_Cert);
}

Certificate::operator mbedtls_x509_crt*() {
    return &m_Cert;
}

Certificate::operator const mbedtls_x509_crt*() const {
    return &m_Cert;
}

ByteSpan Certificate::GetCertificateDer() const {
    return ByteSpan(m_Cert.raw.p, m_Cert.raw.len);
}

int Certificate::VerifyMessage(const ByteSpan& data, const ByteSpan& signature, mbedtls_md_type_t hashType) {
    return m_Signature.Verify(data, signature, hashType);
}

int Certificate::VerifyMessageDigest(const ByteSpan& digest, mbedtls_md_type_t digestType, const ByteSpan& signature) {
    return m_Signature.VerifyDigest(digest, digestType, signature);
}

int Certificate::VerifyChildCertificate(Certificate& childCert) {
    uint32_t flags = 0;
    int res = mbedtls_x509_crt_verify(&childCert.m_Cert, &m_Cert, nullptr, nullptr, &flags, nullptr, nullptr);
    if (res == MBEDTLS_ERR_X509_CERT_VERIFY_FAILED) {
        return flags;
    }
    else {
        return res;
    }
}