#include "lwdn_TLSSocket.h"

#include <exception>
#include <string>
#include <stdexcept>
#include <cerrno>
#include <cassert>
#include "mbedtls/net_sockets.h"
#include "esp_err.h"
#include "esp_log.h"

namespace lwdn {

    TLSConfig::TLSConfig(mbedtls_ssl_config& sslConfig) :
        m_SSLConfig{ sslConfig }
    {
    }

    void TLSConfig::EnableSessionTickets(mbedtls_ssl_ticket_context* ticketContext) {
        m_TicketContext = ticketContext;
        // base - will be overwritten when used with TLSContext
        mbedtls_ssl_conf_session_tickets_cb(&m_SSLConfig, mbedtls_ssl_ticket_write, mbedtls_ssl_ticket_parse, m_TicketContext);
    }

    TLSContext::TLSContext(TLSConfig& config) :
        m_Config{ config.m_SSLConfig },
        m_TicketContext{ config.m_TicketContext }
    {
        mbedtls_ssl_init(&m_SSLContext);

        if (m_TicketContext) {
            mbedtls_ssl_conf_session_tickets_cb(&m_Config, SSLTicketWriteFunc, SSLTicketParseFunc, this);
        }
        m_OriginalVerifyFunc = m_Config.MBEDTLS_PRIVATE(f_vrfy);
        m_OriginalVerifyCtx = m_Config.MBEDTLS_PRIVATE(p_vrfy);
        mbedtls_ssl_conf_verify(&m_Config, SSLVerifyFunc, this);

        int err = mbedtls_ssl_setup(&m_SSLContext, &m_Config);
        if (err != 0) {
            mbedtls_ssl_free(&m_SSLContext);
            ESP_ERROR_CHECK(err);
        }
    }

    TLSContext::~TLSContext() {
        mbedtls_ssl_free(&m_SSLContext);
    }

    mbedtls_ssl_context* TLSContext::GetSSLContext() {
        return &m_SSLContext;
    }

    const mbedtls_ssl_context* TLSContext::GetSSLContext() const {
        return &m_SSLContext;
    }

    TLSContext::operator mbedtls_ssl_context* () {
        return &m_SSLContext;
    }

    TLSContext::operator const mbedtls_ssl_context* () const {
        return &m_SSLContext;
    }

    ByteSpan TLSContext::GetTicketExtraData() const {
        return m_TicketExtraData;
    }

    void TLSContext::AddTicketExtraData(const ByteSpan& data) {
        m_TicketExtraData.insert(m_TicketExtraData.end(), data.begin(), data.end());
    }

    void TLSContext::SetCustomVerifyCallback(CertVerifyCallback&& callback) {
        m_CustomVerifyCallback = std::move(callback);
    }

    int TLSContext::WriteSSLTicket(const mbedtls_ssl_session* session, unsigned char* start, const unsigned char* end, size_t* tlen, uint32_t* lifetime) {
        if (!m_TicketContext) {
            return MBEDTLS_ERR_SSL_INTERNAL_ERROR;
        }
        int baseResult = mbedtls_ssl_ticket_write(m_TicketContext, session, start, end, tlen, lifetime);
        if (baseResult != 0) {
            return baseResult;
        }
        unsigned char* ticketEnd = start + *tlen;
        size_t extraSize = m_TicketExtraData.size();
        size_t trailerSize = extraSize + sizeof(uint8_t);
        if (ticketEnd + trailerSize > end) {
            return MBEDTLS_ERR_SSL_BUFFER_TOO_SMALL;
        }
        memcpy(ticketEnd, m_TicketExtraData.data(), extraSize);
        ticketEnd[extraSize] = extraSize;
        *tlen += trailerSize;
        return 0;
    }

    int TLSContext::ParseSSLTicket(mbedtls_ssl_session* session, unsigned char* buf, size_t len) {
        if (!m_TicketContext) {
            return MBEDTLS_ERR_SSL_INTERNAL_ERROR;
        }
        if (len < m_TicketExtraData.size()) {
            return MBEDTLS_ERR_SSL_BUFFER_TOO_SMALL;
        }
        size_t extraSize = buf[len - 1];
        size_t trailerSize = extraSize + sizeof(uint8_t);
        if (trailerSize > len) {
            return MBEDTLS_ERR_SSL_BAD_INPUT_DATA;
        }
        int result = mbedtls_ssl_ticket_parse(m_TicketContext, session, buf, len - trailerSize);
        if (result == 0) {
            // do not do this if parsing failed, otherwise we would restore data from an expired
            // or malicious ticket
            m_TicketExtraData.assign(buf + len - trailerSize, buf + len - 1);
        }
        return 0;
    }

    int TLSContext::VerifyCertificate(mbedtls_x509_crt* crt, int depth, uint32_t* flags) {
        if (m_OriginalVerifyFunc) {
            int result = m_OriginalVerifyFunc(m_OriginalVerifyCtx, crt, depth, flags);
            if (result != 0) {
                return result;
            }
        }
        if (m_CustomVerifyCallback) {
            (*m_CustomVerifyCallback)(crt, depth, flags);
        }
        return 0;
    }

    int TLSContext::SSLTicketWriteFunc(
        void* p_ticket, const mbedtls_ssl_session* session,
        unsigned char* start, const unsigned char* end, size_t* tlen,
        uint32_t* lifetime
    ) {
        TLSContext* context = static_cast<TLSContext*>(p_ticket);
        return context->WriteSSLTicket(session, start, end, tlen, lifetime);
    }

    int TLSContext::SSLTicketParseFunc(
        void* p_ticket, mbedtls_ssl_session* session,
        unsigned char* buf, size_t len
    ) {
        TLSContext* context = static_cast<TLSContext*>(p_ticket);
        return context->ParseSSLTicket(session, buf, len);
    }

    int TLSContext::SSLVerifyFunc(void* ctx, mbedtls_x509_crt* crt, int depth, uint32_t* flags) {
        TLSContext* context = static_cast<TLSContext*>(ctx);
        return context->VerifyCertificate(crt, depth, flags);
    }

    static constexpr const char* TAG = "TLSSocket";

    TLSSocket::TLSSocket(std::unique_ptr<Socket> base, TLSConfig& sslConfig) :
        m_Base{ std::move(base) },
        m_TLSContext(sslConfig)
    {
        mbedtls_ssl_set_bio(m_TLSContext, this, SSLFnSend, SSLFnRecv, SSLFnRecvTimeout);
    }

    TLSSocket::~TLSSocket()
    {
        mbedtls_ssl_free(m_TLSContext);
    }

    Socket& TLSSocket::GetBaseSocket()
    {
        return *m_Base;
    }

    std::unique_ptr<Socket> TLSSocket::ExtractBaseSocket()
    {
        return std::move(m_Base);
    }

    TLSContext& TLSSocket::GetTLSContext()
    {
        return m_TLSContext;
    }

    bool TLSSocket::IsCloseNotifyReceived() const
    {
        return m_ReceivedCloseNotify;
    }

    bool IsAsyncReturnCode(int code) {
        return code == MBEDTLS_ERR_SSL_WANT_READ ||
            code == MBEDTLS_ERR_SSL_WANT_WRITE ||
            code == MBEDTLS_ERR_SSL_CRYPTO_IN_PROGRESS ||
            code == MBEDTLS_ERR_SSL_ASYNC_IN_PROGRESS;
    }

    int TLSSocket::StartTLS() {
        if (m_SSLReady) {
            return 0;
        }
        while (true) {
            int ret = mbedtls_ssl_handshake(m_TLSContext);
            if (ret == 0) {
                m_SSLReady = true;
                return 0;
            }
            else if (!IsAsyncReturnCode(ret)) {
                ESP_LOGE(TAG, "TLS handshake failed with error: -0x%X", -ret);
                mbedtls_ssl_session_reset(m_TLSContext);
                return SignalError(ret);
            }
        }
    }

    int TranslateErrorToStd(int mbedError) {
        if (mbedError == 0) {
            return 0;
        }
        switch (mbedError) {
        case MBEDTLS_ERR_NET_CONN_RESET:
        case MBEDTLS_ERR_SSL_CONN_EOF:
            return ECONNRESET;
        case MBEDTLS_ERR_SSL_TIMEOUT:
            return ETIMEDOUT;
        default:
            return EIO;
        }
    }

    int TLSSocket::Write(const void* data, size_t len, size_t* sentLen)
    {
        if (m_LastError) {
            return m_LastError;
        }
        int ret = StartTLS();
        if (ret != 0) {
            return TranslateErrorToStd(ret);
        }
        const unsigned char* buf = static_cast<const unsigned char*>(data);
        size_t totalSent = 0;
        while (true) {
            // "This function will do partial writes in some cases.
            // If the return value is non-negative but less than length,
            // the function must be called again with updated arguments:
            // buf + ret, len - ret (if ret is the return value)
            // until it returns a value equal to the last 'len' argument."
            ret = mbedtls_ssl_write(m_TLSContext, buf + totalSent, len - totalSent);
            if (ret > 0) {
                totalSent += ret;
                if (totalSent >= len) {
                    if (sentLen) {
                        *sentLen = totalSent;
                    }
                    return 0;
                }
            }
            else if (ret == MBEDTLS_ERR_SSL_PEER_CLOSE_NOTIFY) {
                HandleCloseNotify();
                return ECONNRESET;
            }
            else if (!IsAsyncReturnCode(ret)) {
                mbedtls_ssl_session_reset(m_TLSContext);
                ESP_LOGE(TAG, "TLS write failed with error: -0x%X", -ret);
                return TranslateErrorToStd(SignalError(ret));
            }
        }
    }

    int TLSSocket::Read(void* buffer, size_t len, size_t* receivedLen, size_t timeout)
    {
        if (m_LastError) {
            return m_LastError;
        }

        int ret = StartTLS();
        if (ret != 0) {
            return TranslateErrorToStd(ret);
        }
        unsigned char* buf = static_cast<unsigned char*>(buffer);
        size_t totalReceived = 0;
        while (true) {
            ret = mbedtls_ssl_read(m_TLSContext, buf + totalReceived, len - totalReceived);
            if (ret > 0) {
                totalReceived += ret;
                if (totalReceived >= len) {
                    if (receivedLen) {
                        *receivedLen = totalReceived;
                    }
                    return 0;
                }
            }
            else if (ret == 0) {
                ESP_LOGI(TAG, "TLS connection closed by peer without close notify");
                return ECONNRESET;
            }
            else if (ret == MBEDTLS_ERR_SSL_PEER_CLOSE_NOTIFY) {
                HandleCloseNotify();
                return ECONNRESET;
            }
            else if (!IsAsyncReturnCode(ret)) {
                ESP_LOGE(TAG, "TLS read failed with error: -0x%X", -ret);
                return TranslateErrorToStd(SignalError(ret));
            }
        }
    }

    void TLSSocket::HandleCloseNotify()
    {
        ESP_LOGI(TAG, "Peer sent TLS close notify");
        m_ReceivedCloseNotify = true;
        mbedtls_ssl_close_notify(m_TLSContext);
    }

    int TLSSocket::SignalError(int err)
    {
        m_LastError = err;
        mbedtls_ssl_session_reset(m_TLSContext);
        return err;
    }

    int TranslateErrnoToMbed(int err, int wouldBlockCode, int defaultFailedCode) {
        if (err == 0) {
            return 0;
        }
        switch (err) {
        case ECONNRESET:
            return MBEDTLS_ERR_NET_CONN_RESET;
        case ETIMEDOUT:
            return MBEDTLS_ERR_SSL_TIMEOUT;
        case EWOULDBLOCK:
            return wouldBlockCode;
        }
        return defaultFailedCode;
    }

    int TLSSocket::SSLFnSend(void* ctx, const unsigned char* buf, size_t len)
    {
        TLSSocket* socket = static_cast<TLSSocket*>(ctx);
        size_t sentLen;
        if (socket->m_Base->Write(buf, len, &sentLen) == 0) {
            return (int)sentLen;
        }
        else {
            return TranslateErrnoToMbed(errno, MBEDTLS_ERR_SSL_WANT_WRITE, MBEDTLS_ERR_NET_SEND_FAILED);
        }
    }

    int TLSSocket::SSLFnRecv(void* ctx, unsigned char* buf, size_t len)
    {
        TLSSocket* socket = static_cast<TLSSocket*>(ctx);
        size_t receivedLen;
        if (socket->m_Base->Read(buf, len, &receivedLen) == 0) {
            return (int)receivedLen;
        }
        else {
            return TranslateErrnoToMbed(errno, MBEDTLS_ERR_SSL_WANT_READ, MBEDTLS_ERR_NET_RECV_FAILED);
        }
    }

    int TLSSocket::SSLFnRecvTimeout(void* ctx, unsigned char* buf, size_t len, uint32_t timeout)
    {
        TLSSocket* socket = static_cast<TLSSocket*>(ctx);
        size_t receivedLen;
        size_t realTimeout = timeout == 0 ? SIZE_MAX : timeout; //mbed 0 = no timeout, but our API uses SIZE_MAX for that, so convert it back
        if (socket->m_Base->Read(buf, len, &receivedLen, realTimeout) == 0) {
            return (int)receivedLen;
        }
        else {
            return TranslateErrnoToMbed(errno, MBEDTLS_ERR_SSL_WANT_READ, MBEDTLS_ERR_NET_RECV_FAILED);
        }
    }

    LinkAdapter* TLSSocket::GetLinkAdapter() const
    {
        return m_Base->GetLinkAdapter();
    }
}