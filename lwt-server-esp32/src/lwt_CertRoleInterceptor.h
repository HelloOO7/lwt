#pragma once

#include "lwtp_X509AuthInterceptor.h"
#include "lwt_CertRole.h"

namespace lwt {

    extern lwtp::Server::SocketSession::Tag CERT_ROLE_MASK_TAG;

    class CertRoleInterceptor : public lwtp::X509AuthInterceptor {
    public:
        virtual void HandleClientCertificate(lwtp::Server::SocketSession& session, const mbedtls_x509_crt* clientCert) override;

        static CertRole ParseCertRole(const std::string& role);
    };
}