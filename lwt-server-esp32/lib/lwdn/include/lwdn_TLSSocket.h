#pragma once

#include "lwdn_Socket.h"

#include "mbedtls/ssl.h"

namespace lwdn {

    class TLSSocket : public Socket {
    private:
        Socket& m_Base;
        mbedtls_ssl_context m_SSLContext;
        bool m_SSLReady{ false };
        int m_LastError{ 0 };

    public:
        TLSSocket(Socket& base, mbedtls_ssl_config& sslConfig);
        ~TLSSocket() override;

        int Write(const void* data, size_t len, size_t* sentLen = nullptr) override;
        int Read(void* buffer, size_t len, size_t* receivedLen = nullptr, size_t timeout = SIZE_MAX) override;

    private:
        int StartTLS();
        int SignalError(int err);

        static int SSLFnSend(void* ctx, const unsigned char* buf, size_t len);
        static int SSLFnRecv(void* ctx, unsigned char* buf, size_t len);
        static int SSLFnRecvTimeout(void* ctx, unsigned char* buf, size_t len, uint32_t timeout);
    };
}