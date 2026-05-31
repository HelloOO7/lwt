#pragma once 

#include "lwdn_ServerSocket.h"
#include "lwdn_TLSSocket.h"

namespace lwdn {

    class TLSServerSocket : public ServerSocket {
    private:
        ServerSocket& m_Base;
        mbedtls_ssl_config& m_SSLConfig;
    public:
        TLSServerSocket(ServerSocket& base, mbedtls_ssl_config& sslConfig);

        std::unique_ptr<Socket> Accept() override;
    };
}