#pragma once

#include "lwdn_Socket.h"
#include <memory>
#include "mbedtls/ssl.h"
#include "mbedtls/ssl_ticket.h"
#include "PSRAMContainers.h"
#include <functional>
#include <optional>
#include "CommonTypes.h"

namespace lwdn {

    class TLSContext;

    class TLSConfig {
        friend class TLSContext;
    private:
        mbedtls_ssl_config& m_SSLConfig;
        mbedtls_ssl_ticket_context* m_TicketContext{ nullptr };

    public:
        TLSConfig(mbedtls_ssl_config& sslConfig);

        void EnableSessionTickets(mbedtls_ssl_ticket_context* ticketContext);
    };

    class TLSContext {
    public:
        using CertVerifyCallback = std::function<void(mbedtls_x509_crt* crt, int depth, uint32_t* flags)>;

    private:
        using VerifyFunc = int (*)(void*, mbedtls_x509_crt*, int, uint32_t*);

        mbedtls_ssl_config  m_Config; //we need to create our own copy of the config, because it stores the pointer to the ticket context
        mbedtls_ssl_context m_SSLContext;

        mbedtls_ssl_ticket_context* m_TicketContext{ nullptr };
        ByteVector m_TicketExtraData;

        VerifyFunc m_OriginalVerifyFunc{ nullptr };
        void* m_OriginalVerifyCtx{ nullptr };

        std::optional<CertVerifyCallback> m_CustomVerifyCallback;

    public:
        TLSContext(TLSConfig& config);
        ~TLSContext();

        mbedtls_ssl_context* GetSSLContext();
        const mbedtls_ssl_context* GetSSLContext() const;

        operator mbedtls_ssl_context* ();
        operator const mbedtls_ssl_context* () const;

        ByteSpan GetTicketExtraData() const;
        void AddTicketExtraData(const ByteSpan& data);

        void SetCustomVerifyCallback(CertVerifyCallback&& callback);

    private:
        int WriteSSLTicket(const mbedtls_ssl_session* session, unsigned char* start, const unsigned char* end, size_t* tlen, uint32_t* lifetime);
        int ParseSSLTicket(mbedtls_ssl_session* session, unsigned char* buf, size_t len);
        int VerifyCertificate(mbedtls_x509_crt* crt, int depth, uint32_t* flags);

        static int SSLTicketWriteFunc(
            void* p_ticket, const mbedtls_ssl_session* session,
            unsigned char* start, const unsigned char* end, size_t* tlen,
            uint32_t* lifetime
        );

        static int SSLTicketParseFunc(
            void* p_ticket, mbedtls_ssl_session* session,
            unsigned char* buf, size_t len
        );

        static int SSLVerifyFunc(void* ctx, mbedtls_x509_crt* crt, int depth, uint32_t* flags);
    };

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
        TLSContext m_TLSContext;
        bool m_SSLReady{ false };
        bool m_ReceivedCloseNotify{ false };
        int m_LastError{ 0 };

    public:
        TLSSocket(std::unique_ptr<Socket> base, TLSConfig& sslConfig);
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

        TLSContext& GetTLSContext();
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