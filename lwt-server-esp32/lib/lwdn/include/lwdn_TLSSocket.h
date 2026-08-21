#pragma once

#include "lwdn_Socket.h"
#include <memory>
#include "mbedtls/ssl.h"

namespace lwdn {

    /**
     * @brief A socket wrapper implementing TLS transport over a regular socket.
     * The TLSSocket represents one TLS connection, so it is not reusable after the connection is closed
     * (via a close notify alert), even if the socket may be still open. If the client wishes to continue
     * plaintext transport after a TLS session has ended, it should check IsCloseNotifyReceived() after an
     * ECONNRESET error from one of the Read/Write functions, and reattempt it over the original socket.
     * An actual connection reset would trigger an ECONNRESET on that socket as well, otherwise, communication
     * can continue as normal.
     */
    class TLSSocket : public Socket {
    private:
        std::unique_ptr<Socket> m_Base;
        mbedtls_ssl_context m_SSLContext;
        bool m_SSLReady{ false };
        bool m_ReceivedCloseNotify{ false };
        int m_LastError{ 0 };

    public:
        TLSSocket(std::unique_ptr<Socket> base, mbedtls_ssl_config& sslConfig);
        ~TLSSocket() override;

        Socket& GetBaseSocket();
        /**
         * @brief Obtains the underlying socket used for transport, releasing ownership from the TLSSocket.
         * This means that the socket will not be closed when the TLSSocket destructor is run.
         * After calling this method, the TLSSocket must not be used anymore in any way other than being destroyed.
         * 
         * @return std::unique_ptr<Socket> 
         */
        std::unique_ptr<Socket> ExtractBaseSocket();

        mbedtls_ssl_context& GetSSLContext();
        bool IsCloseNotifyReceived() const;

        int Write(const void* data, size_t len, size_t* sentLen = nullptr) override;
        int Read(void* buffer, size_t len, size_t* receivedLen = nullptr, size_t timeout = SIZE_MAX) override;

        LinkAdapter* GetLinkAdapter() const override;

    private:
        int StartTLS();
        int SignalError(int err);
        void HandleCloseNotify();

        static int SSLFnSend(void* ctx, const unsigned char* buf, size_t len);
        static int SSLFnRecv(void* ctx, unsigned char* buf, size_t len);
        static int SSLFnRecvTimeout(void* ctx, unsigned char* buf, size_t len, uint32_t timeout);
    };
}