#pragma once

#include "lwtp_Server.h"
#include "mbedtls/ssl.h"

namespace lwtp {

    class StartTLSInterceptor : public SocketInterceptor {
    private:
        mbedtls_ssl_config& m_SSLConfig;
    public:
        /**
         * @brief Create a StartTLSInterceptor. The SSL configuration is not owned by the interceptor - it must
         * be ensured that it remains valid for the entire time the interceptor is used, and that it is properly
         * initialized for use with the TLSSocket created by this interceptor.
         *
         * @param sslConfig the SSL config
         */
        StartTLSInterceptor(mbedtls_ssl_config& sslConfig);

        Server::SocketSession::Flag GetUsedFlagCount() const override;

        Packet Intercept(Server::SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain, Server::SocketSession::Flag sessionFlagBase) override;
        int InterceptError(Server::SocketSession& session, int error, SocketInterceptor::Chain& chain, Server::SocketSession::Flag sessionFlagBase) override;
    };
}