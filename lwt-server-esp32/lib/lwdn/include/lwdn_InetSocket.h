#pragma once

#include "lwdn_Socket.h"

namespace lwdn {

    class InetSocket : public Socket {
    private:
        lwdn::LinkAdapter* m_LinkAdapter;
        int m_SocketFd;

    public:
        InetSocket(lwdn::LinkAdapter* linkAdapter, int socketFd);
        virtual ~InetSocket();

        virtual int Write(const void* data, size_t len, size_t* sentLen = nullptr) override;
        virtual int Read(void* buffer, size_t len, size_t* receivedLen = nullptr, size_t timeout = SIZE_MAX) override;

        virtual LinkAdapter* GetLinkAdapter() const override { return m_LinkAdapter; }
    };
}