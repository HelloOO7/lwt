#include "lwtp_TLSInterceptor.h"
#include "lwdn_TLSSocket.h"
#include <utility>
#include "esp_log.h"

namespace lwtp {

    SocketSession::Tag TLS_CONTEXT_TAG{};

    SocketInterceptor::Event EVENT_CERT_VERIFY{};

    StartTLSInterceptor::StartTLSInterceptor(lwdn::TLSConfig& sslConfig) :
        m_SSLConfig(sslConfig)
    {
    }

    void SetupTLSSocket(lwdn::TLSSocket& socket, SocketSession& session, SocketInterceptor::Chain& chain)
    {
        auto context = &socket.GetTLSContext(); // must be done before std::move!
        context->SetCustomVerifyCallback(
            [&session, &chain](mbedtls_x509_crt* crt, int depth, uint32_t* flags) {
                ESP_LOGI("CV", "Certificate verification callback: depth=%d, flags=0x%08X", depth, *flags);
                TLSEventCertVerify eventData{ crt, depth, flags };
                chain.Traverse(session, &EVENT_CERT_VERIFY, &eventData);
            }
        );
        session.SetTag(&TLS_CONTEXT_TAG, context);
    }

    Packet StartTLSInterceptor::Intercept(SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain)
    {
        if (request.IsControlMessage()) {
            if (request.ParseControlCommand() == ControlCommand::START_TLS) {
                if (!session.HasTag(&TLS_CONTEXT_TAG)) {
                    auto tlsSocket = std::make_unique<lwdn::TLSSocket>(session.ExtractSocket(), m_SSLConfig);

                    // must be done before std::move!
                    SetupTLSSocket(*tlsSocket, session, chain);

                    session.ChangeSocket(std::move(tlsSocket));

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

    int StartTLSInterceptor::InterceptError(SocketSession& session, int error, SocketInterceptor::Chain& chain)
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

    void ImplicitTLSInterceptor::InterceptSocketEvent(SocketSession& session, SocketInterceptor::Event* event, void* eventData, SocketInterceptor::Chain& chain)
    {
        if (event == &EVENT_SOCKET_ACCEPTED) {
            auto tlsSocket = std::make_unique<lwdn::TLSSocket>(session.ExtractSocket(), m_SSLConfig);
            SetupTLSSocket(*tlsSocket, session, chain);
            session.ChangeSocket(std::move(tlsSocket));
        }
    }

    void ContextTagTLSInterceptor::InterceptSocketEvent(SocketSession& session, SocketInterceptor::Event* event, void* eventData, SocketInterceptor::Chain& chain)
    {
        if (event == &EVENT_SOCKET_ACCEPTED) {
            auto&& tlsSocket = static_cast<lwdn::TLSSocket&>(session.GetSocket());
            SetupTLSSocket(tlsSocket, session, chain);
        }
    }
}