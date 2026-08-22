#pragma once

#include "lwtp_Server.h"
#include "mbedtls/ssl.h"
#include "lwdn_TLSSocket.h"

namespace lwtp {

    extern SocketSession::Tag TLS_CONTEXT_TAG;

    extern SocketInterceptor::Event EVENT_CERT_VERIFY;

    struct TLSEventCertVerify {
        mbedtls_x509_crt* crt;
        int depth;
        uint32_t* flags;
    };

    /**
     * @brief Add this interceptor to a plain text server socket to allow clients to request a TLS upgrade using the START_TLS control command.
     */
    class StartTLSInterceptor : public SocketInterceptor {
    private:
        lwdn::TLSConfig& m_SSLConfig;
    public:
        /**
         * @brief Create a StartTLSInterceptor. The SSL configuration is not owned by the interceptor - it must
         * be ensured that it remains valid for the entire time the interceptor is used, and that it is properly
         * initialized for use with the TLSSocket created by this interceptor.
         *
         * @param sslConfig the SSL config
         */
        StartTLSInterceptor(lwdn::TLSConfig& sslConfig);

        Packet Intercept(SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain) override;
        int InterceptError(SocketSession& session, int error, SocketInterceptor::Chain& chain) override;
    };

    /**
     * @brief Add this interceptor to a server socket to automatically wrap all accepted sockets in TLS.
     * This is similar to a server accepting HTTPS connections on port 443 - attempting to use cleartext traffic
     * is equivalent to miscommunication.
     */
    class ImplicitTLSInterceptor : public SocketInterceptor {
    private:
        lwdn::TLSConfig& m_SSLConfig;
    public:
        ImplicitTLSInterceptor(lwdn::TLSConfig& sslConfig);

        Packet Intercept(SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain) override;
        void InterceptSocketEvent(SocketSession& session, SocketInterceptor::Event* event, void* eventData, SocketInterceptor::Chain& chain) override;
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
        Packet Intercept(SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain) override;
        void InterceptSocketEvent(SocketSession& session, SocketInterceptor::Event* event, void* eventData, SocketInterceptor::Chain& chain) override;
    };
}