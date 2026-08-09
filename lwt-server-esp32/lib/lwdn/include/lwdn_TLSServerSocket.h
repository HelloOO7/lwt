#pragma once 

#include "lwdn_ServerSocket.h"
#include "lwdn_TLSSocket.h"

namespace lwdn {

    /**
     * @brief Implementation of a LWDN server socket that wraps another server socket with
     * implicit TLS support. Compared to the START_TLS command in LWTP, this encrypts all
     * communication from start to finish, barring the initial handshake. However, it does not
     * support negotiation if the client wants to use TLS or not, so if an "optionally secure"
     * endpoint is desired without using a separate endpoint (port/PSM), the LWTP StartTLSInterceptor
     * can be used to start encrypted communication using control messages.
     */
    class TLSServerSocket : public ServerSocket {
    private:
        ServerSocket& m_Base;
        mbedtls_ssl_config& m_SSLConfig;
    public:
        TLSServerSocket(ServerSocket& base, mbedtls_ssl_config& sslConfig);

        std::unique_ptr<Socket> Accept() override;

        virtual LinkAdapter* GetLinkAdapter() const override;
    };
}