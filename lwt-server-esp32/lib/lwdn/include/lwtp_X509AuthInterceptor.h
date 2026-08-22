#pragma once

#include "lwtp_Server.h"
#include "mbedtls/ssl.h"
#include "lwdn_TLSSocket.h"

namespace lwtp {

    class X509AuthInterceptor : public SocketInterceptor {
    public:
        X509AuthInterceptor();

        Packet Intercept(SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain) override;
        void InterceptSocketEvent(SocketSession& session, SocketInterceptor::Event* event, void* eventData, SocketInterceptor::Chain& chain) override;

        /**
         * @brief Method to implement in subclasses to handle the client certificate.
         * Be prepared to handle the case where clientCert is nullptr, which can occur when session resumption
         * was used and no certificate had to be sent. In this case, use the TLSContext's session ticket extra data
         * feature to store any relevant information that would have been extracted from the certificate,
         * so that it can be restored on session resumption. This method is called every time a leaf certificate
         * is sent by the client. It is NOT called if session tickets were used, as the client does not send a certificate in that case.
         * Therefore, you should also override the \ref HandleSessionResumption method and restore any relevant information there.
         *
         * @param session
         * @param tlsContext
         * @param clientCert
         */
        virtual void HandleClientCertificate(SocketSession& session, lwdn::TLSContext& tlsContext, const mbedtls_x509_crt* clientCert);

        /**
         * @brief Called at the start of every request to restore state from the TLSContext's session ticket extra data, if any.
         * This is called with every request, regardless of whether session resumption was used or not. In any case though,
         * the session ticket extra data se in HandleClientCertificate is available at this point.
         * 
         * @param session 
         * @param tlsContext 
         */
        virtual void HandleSessionResumption(SocketSession& session, lwdn::TLSContext& tlsContext);
    };
}