#pragma once

#include "lwtp_Server.h"
#include "mbedtls/ssl.h"

namespace lwtp {

    class X509AuthInterceptor : public SocketInterceptor {
    public:
        X509AuthInterceptor();

        Packet Intercept(Server::SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain) override;

        virtual void HandleClientCertificate(Server::SocketSession& session, const mbedtls_x509_crt* clientCert);
    };
}