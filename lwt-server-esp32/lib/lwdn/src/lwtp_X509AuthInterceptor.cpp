#include "lwtp_X509AuthInterceptor.h"

#include "lwtp_TLSInterceptor.h"
#include "esp_log.h"
#include "lwdn_TLSSocket.h"

namespace lwtp {

    static constexpr const char* TAG = "X509AuthInterceptor";

    X509AuthInterceptor::X509AuthInterceptor()
    {
    }

    Packet X509AuthInterceptor::Intercept(SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain)
    {
        lwdn::TLSContext* tlsContext = nullptr;
        if (session.GetTag(&TLS_CONTEXT_TAG, (void**)&tlsContext)) {
            HandleSessionResumption(session, *tlsContext);
        }

        return chain.Proceed(session, request);
    }

    void X509AuthInterceptor::InterceptSocketEvent(SocketSession& session, SocketInterceptor::Event* event, void* eventData, SocketInterceptor::Chain& chain)
    {
        if (event != &EVENT_CERT_VERIFY) {
            return chain.Proceed(session, event, eventData);
        }

        // This must be done during the handshake and not after. The reason is that we need to be able to modify session ticket
        // data before it is sent to the client, so that it can be restored on session resumption.

        lwdn::TLSContext* tlsContext = nullptr;
        if (session.GetTag(&TLS_CONTEXT_TAG, (void**)&tlsContext)) {
            TLSEventCertVerify* certVerifyData = static_cast<TLSEventCertVerify*>(eventData);

            if (*certVerifyData->flags != 0) {
                if (*certVerifyData->flags & MBEDTLS_X509_BADCERT_SKIP_VERIFY) {
                    HandleClientCertificate(session, *tlsContext, nullptr);
                }
                else {
                    ESP_LOGW(TAG, "Client certificate at depth %u verification failed: 0x%08X", certVerifyData->depth, *certVerifyData->flags);
                }
            }
            else {
                if (certVerifyData->depth == 0) {
                    HandleClientCertificate(session, *tlsContext, certVerifyData->crt);
                }
            }
        }
        else {
            ESP_LOGW(TAG, "TLS context tag missing ??");
        }

        return chain.Proceed(session, event, eventData);
    }

    void X509AuthInterceptor::HandleClientCertificate(SocketSession& session, lwdn::TLSContext& tlsContext, const mbedtls_x509_crt* clientCert)
    {
        // Default implementation does nothing. Override this method in a derived class to handle the client certificate.
        // This should be used to process authentication and extract relevant information from the cert, which can be stored as tags.
    }

    void X509AuthInterceptor::HandleSessionResumption(SocketSession& session, lwdn::TLSContext& tlsContext)
    {

    }
}