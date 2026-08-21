#include "lwtp_TLSInterceptor.h"
#include "lwdn_TLSSocket.h"
#include <utility>
#include "esp_log.h"

namespace lwtp {

    Server::SocketSession::Tag TLS_CONTEXT_TAG{};

    StartTLSInterceptor::StartTLSInterceptor(mbedtls_ssl_config& sslConfig) :
        m_SSLConfig(sslConfig)
    {
    }

    Packet StartTLSInterceptor::Intercept(Server::SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain)
    {
        if (request.IsControlMessage()) {
            if (request.ParseControlCommand() == ControlCommand::START_TLS) {
                if (!session.HasTag(&TLS_CONTEXT_TAG)) {
                    auto tlsSocket = std::make_unique<lwdn::TLSSocket>(session.ExtractSocket(), m_SSLConfig);
                    auto context = &tlsSocket->GetSSLContext(); // must be done before std::move!
                    session.ChangeSocket(std::move(tlsSocket));
                    session.SetTag(&TLS_CONTEXT_TAG, context);

                    return Server::CreateControlCommandResponse(request.GetHeader(), ControlCommand::GO_AHEAD);
                }
                else {
                    // already using TLS, can't start another one
                    return Server::CreateControlCommandResponse(request.GetHeader(), ControlCommand::BAD_SEQUENCE_OF_COMMANDS);
                }
            }
        }

        return chain.Proceed(session, request);
    }

    int StartTLSInterceptor::InterceptError(Server::SocketSession& session, int error, SocketInterceptor::Chain& chain)
    {
        if (error == ECONNRESET && session.HasTag(&TLS_CONTEXT_TAG)) {
            auto&& tlsSocket = static_cast<lwdn::TLSSocket&>(session.GetSocket());
            if (tlsSocket.IsCloseNotifyReceived()) {
                // close TLS channel, let client attempt to continue over original socket if desired (if this was really a reset, it will just fail again)
                session.ChangeSocket(tlsSocket.ExtractBaseSocket());
                session.RemoveTag(&TLS_CONTEXT_TAG);
                return EAGAIN;
            }
        }
        return chain.Proceed(session, error);
    }

    Packet ImplicitTLSInterceptor::Intercept(Server::SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain)
    {
        return chain.Proceed(session, request);
    }

    void ImplicitTLSInterceptor::InterceptOpenSocket(Server::SocketSession& session, SocketInterceptor::Chain& chain)
    {
        auto tlsSocket = std::make_unique<lwdn::TLSSocket>(session.ExtractSocket(), m_SSLConfig);
        session.ChangeSocket(std::move(tlsSocket));
        session.SetTag(&TLS_CONTEXT_TAG, &tlsSocket->GetSSLContext());
    }

    Packet ContextTagTLSInterceptor::Intercept(Server::SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain)
    {
        return chain.Proceed(session, request);
    }

    void ContextTagTLSInterceptor::InterceptOpenSocket(Server::SocketSession& session, SocketInterceptor::Chain& chain)
    {
        auto&& tlsSocket = static_cast<lwdn::TLSSocket&>(session.GetSocket());
        session.SetTag(&TLS_CONTEXT_TAG, &tlsSocket.GetSSLContext());
    }
}