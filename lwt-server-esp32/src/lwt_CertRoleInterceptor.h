#pragma once

#include "lwtp_X509AuthInterceptor.h"
#include "lwt_CertRole.h"

namespace lwt {

    extern lwtp::SocketSession::Tag CERT_ROLE_MASK_TAG;

    class CertRoleInterceptor : public lwtp::X509AuthInterceptor {
    public:
        virtual void HandleClientCertificate(lwtp::SocketSession& session, lwdn::TLSContext& tlsContext, const mbedtls_x509_crt* clientCert) override;
        virtual void HandleSessionResumption(lwtp::SocketSession& session, lwdn::TLSContext& tlsContext) override;
    };
}