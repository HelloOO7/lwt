#include "lwdn_InetSocket.h"

#include <sys/socket.h>

namespace lwdn {

    InetSocket::InetSocket(lwdn::LinkAdapter* linkAdapter, int socketFd)
        : m_LinkAdapter(linkAdapter), m_SocketFd(socketFd)
    {
    }

    InetSocket::~InetSocket() {
        if (m_SocketFd >= 0) {
            shutdown(m_SocketFd, SHUT_RDWR);
            close(m_SocketFd);
            m_SocketFd = -1;
        }
    }

    int InetSocket::Write(const void* data, size_t len, size_t* sentLen) {
        ssize_t result = send(m_SocketFd, data, len, 0);
        if (result < 0) {
            return errno;
        }
        if (sentLen) {
            *sentLen = static_cast<size_t>(result);
        }
        return 0;
    }

    int InetSocket::Read(void* buffer, size_t len, size_t* receivedLen, size_t timeout) {
        if (timeout != SIZE_MAX) {
            struct timeval tv;
            tv.tv_sec = timeout / 1000;
            tv.tv_usec = (timeout % 1000) * 1000;
            setsockopt(m_SocketFd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
        }

        ssize_t result = recv(m_SocketFd, buffer, len, 0);
        if (result < 0) {
            return errno;
        }
        if (receivedLen) {
            *receivedLen = static_cast<size_t>(result);
        }
        return 0;
    }

}