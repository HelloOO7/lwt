#pragma once

#include "lwtp_Server.h"
#include "mbedtls/ssl.h"

namespace lwtp {

    extern Server::SocketSession::Tag TLS_CONTEXT_TAG;

    /**
     * @brief Add this interceptor to a plain text server socket to allow clients to request a TLS upgrade using the START_TLS control command.
     */
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

        Packet Intercept(Server::SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain) override;
        int InterceptError(Server::SocketSession& session, int error, SocketInterceptor::Chain& chain) override;
    };

    /**
     * @brief Add this interceptor to a server socket to automatically wrap all accepted sockets in TLS.
     * This is similar to a server accepting HTTPS connections on port 443 - attempting to use cleartext traffic
     * is equivalent to miscommunication.
     */
    class ImplicitTLSInterceptor : public SocketInterceptor {
    private:
        mbedtls_ssl_config& m_SSLConfig;
    public:
        ImplicitTLSInterceptor(mbedtls_ssl_config& sslConfig);

        Packet Intercept(Server::SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain) override;
        void InterceptOpenSocket(Server::SocketSession& session, SocketInterceptor::Chain& chain) override;
    };

    /**
     * @brief Add this interceptor to a \ref TLSServerSocket to ensure that the session has the
     * \ref TLS_CONTEXT_TAG set as if it was created by the \ref StartTLSInterceptor or \ref ImplicitTLSInterceptor.
     * It is absolutely necessary that the socket is actually a \ref TLSSocket, otherwise an illegal static cast
     * will occur. Using this with a \ref StartTLSInterceptor or \ref ImplicitTLSInterceptor is redundant (albeit harmless),
     * as those interceptors already set the tag.
     */
    class ContextTagTLSInterceptor : public SocketInterceptor {
    public:
        Packet Intercept(Server::SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain) override;
        void InterceptOpenSocket(Server::SocketSession& session, SocketInterceptor::Chain& chain) override;
    };
}