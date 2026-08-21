#include "lwtp_X509AuthInterceptor.h"

#include "lwtp_TLSInterceptor.h"
#include "esp_log.h"

namespace lwtp {

    static constexpr const char* TAG = "X509AuthInterceptor";

    X509AuthInterceptor::X509AuthInterceptor()
    {
    }

    Packet X509AuthInterceptor::Intercept(Server::SocketSession& session, const Packet& request, SocketInterceptor::Chain& chain)
    {
        mbedtls_ssl_context* sslContext = nullptr;
        if (session.GetTag(&TLS_CONTEXT_TAG, (void**)&sslContext)) {
            auto verifyResult = mbedtls_ssl_get_verify_result(sslContext);
            if (verifyResult != 0) {
                ESP_LOGW(TAG, "Client certificate verification failed: 0x%08X", verifyResult);
            }
            else {
                const mbedtls_x509_crt* clientCert = mbedtls_ssl_get_peer_cert(sslContext);
                if (clientCert) {
                    HandleClientCertificate(session, clientCert);
                }
            }
        }

        return chain.Proceed(session, request);
    }

    void X509AuthInterceptor::HandleClientCertificate(Server::SocketSession& session, const mbedtls_x509_crt* clientCert)
    {
        // Default implementation does nothing. Override this method in a derived class to handle the client certificate.
        // This should be used to process authentication and extract relevant information from the cert, which can be stored as tags.
    }
}