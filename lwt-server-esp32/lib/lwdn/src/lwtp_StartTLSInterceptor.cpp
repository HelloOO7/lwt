#include "lwtp_StartTLSInterceptor.h"
#include "lwdn_TLSSocket.h"
#include <utility>

namespace lwtp {

    enum SessionFlag : Server::SocketSession::Flag {
        FLAG_TLS_USED,
        FLAG_COUNT
    };

    StartTLSInterceptor::StartTLSInterceptor(mbedtls_ssl_config& sslConfig) :
        m_SSLConfig(sslConfig)
    {
    }

    Server::SocketSession::Flag StartTLSInterceptor::GetUsedFlagCount() const
    {
        return FLAG_COUNT;
    }

    Packet StartTLSInterceptor::Intercept(Server::SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain, Server::SocketSession::Flag sessionFlagBase)
    {
        if (request.IsControlMessage()) {
            if (request.ParseControlCommand() == ControlCommand::START_TLS) {
                if (!session.IsFlagSet(sessionFlagBase, FLAG_TLS_USED)) {
                    auto tlsSocket = std::make_unique<lwdn::TLSSocket>(session.ExtractSocket(), m_SSLConfig);
                    session.ChangeSocket(std::move(tlsSocket));
                    session.SetFlag(sessionFlagBase, FLAG_TLS_USED);

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

    int StartTLSInterceptor::InterceptError(Server::SocketSession& session, int error, SocketInterceptor::Chain& chain, Server::SocketSession::Flag sessionFlagBase)
    {
        if (error == ECONNRESET && session.IsFlagSet(sessionFlagBase, FLAG_TLS_USED)) {
            auto&& tlsSocket = static_cast<lwdn::TLSSocket&>(session.GetSocket());
            if (tlsSocket.IsCloseNotifyReceived()) {
                // close TLS channel, let client attempt to continue over original socket if desired (if this was really a reset, it will just fail again)
                session.ChangeSocket(tlsSocket.ExtractBaseSocket());
                session.ClearFlag(sessionFlagBase, FLAG_TLS_USED);
                return EAGAIN;
            }
        }
        return chain.Proceed(session, error);
    }
}