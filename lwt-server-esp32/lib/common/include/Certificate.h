#pragma once

#include "DigitalSignature.h"
#include "mbedtls/x509_crt.h"

class Certificate {
private:
    mbedtls_x509_crt m_Cert;
    DigitalSignatureImpl m_Signature;

public:
    Certificate(const ByteSpan& certPemOrDer);
    ~Certificate();

    operator mbedtls_x509_crt*();
    operator const mbedtls_x509_crt*() const;
    ByteSpan GetCertificateDer() const;

    int VerifyMessage(const ByteSpan& data, const ByteSpan& signature, mbedtls_md_type_t hashType = MBEDTLS_MD_SHA256);
    int VerifyMessageDigest(const ByteSpan& digest, mbedtls_md_type_t digestType, const ByteSpan& signature);

    int VerifyChildCertificate(Certificate& childCert);
};