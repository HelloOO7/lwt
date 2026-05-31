#include "lwdn_TLSServerSocket.h"

namespace lwdn {

    TLSServerSocket::TLSServerSocket(ServerSocket& base, mbedtls_ssl_config& sslConfig) :
        m_Base{ base },
        m_SSLConfig{ sslConfig }
    {
    }

    std::unique_ptr<Socket> TLSServerSocket::Accept()
    {
        auto baseSocket = m_Base.Accept();
        if (!baseSocket) {
            return nullptr;
        }
        return std::make_unique<TLSSocket>(*baseSocket, m_SSLConfig);
    }
}