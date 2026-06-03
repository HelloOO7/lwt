#include "lwdn_TLSSocket.h"

#include <exception>
#include <string>
#include <stdexcept>
#include <cerrno>
#include <cassert>
#include "mbedtls/net_sockets.h"

namespace lwdn {

    TLSSocket::TLSSocket(Socket& base, mbedtls_ssl_config& sslConfig) :
        m_Base{ base }
    {
        mbedtls_ssl_init(&m_SSLContext);
        int err = mbedtls_ssl_setup(&m_SSLContext, &sslConfig);
        if (err != 0) {
            assert(err == PSA_ERROR_INSUFFICIENT_MEMORY);
            throw std::bad_alloc();
        }
        mbedtls_ssl_set_bio(&m_SSLContext, this, SSLFnSend, SSLFnRecv, SSLFnRecvTimeout);
    }

    TLSSocket::~TLSSocket()
    {
        mbedtls_ssl_free(&m_SSLContext);
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
            int ret = mbedtls_ssl_handshake(&m_SSLContext);
            if (ret == 0) {
                m_SSLReady = true;
                return 0;
            }
            else if (!IsAsyncReturnCode(ret)) {
                mbedtls_ssl_session_reset(&m_SSLContext);
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
            return ECONNRESET;
        case MBEDTLS_ERR_SSL_TIMEOUT:
            return ETIMEDOUT;
        default:
            return mbedError;
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
            ret = mbedtls_ssl_write(&m_SSLContext, buf + totalSent, len - totalSent);
            if (ret > 0) {
                totalSent += ret;
                if (totalSent >= len) {
                    if (sentLen) {
                        *sentLen = totalSent;
                    }
                    return 0;
                }
            }
            else if (!IsAsyncReturnCode(ret)) {
                mbedtls_ssl_session_reset(&m_SSLContext);
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
            ret = mbedtls_ssl_read(&m_SSLContext, buf + totalReceived, len - totalReceived);
            if (ret > 0) {
                totalReceived += ret;
                if (totalReceived >= len) {
                    if (receivedLen) {
                        *receivedLen = totalReceived;
                    }
                    return 0;
                }
            }
            else if (!IsAsyncReturnCode(ret)) {
                return TranslateErrorToStd(SignalError(ret));
            }
        }
    }

    int TLSSocket::SignalError(int err)
    {
        m_LastError = err;
        mbedtls_ssl_session_reset(&m_SSLContext);
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
        if (socket->m_Base.Write(buf, len, &sentLen) == 0) {
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
        if (socket->m_Base.Read(buf, len, &receivedLen) == 0) {
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
        if (socket->m_Base.Read(buf, len, &receivedLen, timeout) == 0) {
            return (int)receivedLen;
        }
        else {
            return TranslateErrnoToMbed(errno, MBEDTLS_ERR_SSL_WANT_READ, MBEDTLS_ERR_NET_RECV_FAILED);
        }
    }
}