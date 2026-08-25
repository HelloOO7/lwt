#include "lwdn_TLSServerSocket.h"

#include "NewAndDelete.h"

namespace lwdn {

    TLSServerSocket::TLSServerSocket(ServerSocket& base, TLSConfig& sslConfig) :
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
        // TLS context can be quite large, so use PSRAM
        return std::unique_ptr<TLSSocket>(new (MALLOC_CAP_SPIRAM) TLSSocket(std::move(baseSocket), m_SSLConfig));
    }

    LinkAdapter* TLSServerSocket::GetLinkAdapter() const
    {
        return m_Base.GetLinkAdapter();
    }
}