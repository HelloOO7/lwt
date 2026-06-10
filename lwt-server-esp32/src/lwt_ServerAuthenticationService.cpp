#include "lwt_ServerAuthenticationService.h"

#include "operations_generated.h"
#include "server_authentication_generated.h"
#include "esp_timer.h"
#include "esp_netif.h"
#include "esp_log.h"

namespace lwt {

    static const char* TAG = "ServerAuthenticationService";

    ServerAuthenticationService::ServerAuthenticationService(uint8_t* certString, mbedtls_pk_context& signingKey, mbedtls_ctr_drbg_context& ctrDrbg) :
        m_CertString(certString),
        m_SigningKey(signingKey),
        m_CtrDrbg(ctrDrbg)
    {
    }

    void ServerAuthenticationService::Register(ServiceRegistry& registry) {
        registry.RegisterServiceCallback(Operation_AuthenticateServer, ApplicationServer::CreateOperationServiceFunc<ServerAuthenticationRequest>(
            [this](const ServerAuthenticationRequest& req, flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                auto signedChallenge = SignChallenge(req.challenge()->data(), req.challenge()->size());
                if (signedChallenge.empty()) {
                    return 550;
                }
                auto challengeRespOffset = fbb.CreateVector(signedChallenge);
                auto certBytesOffset = fbb.CreateVector(m_CertString, strlen((char*)m_CertString));

                fbb.Finish(CreateServerAuthenticationResponse(fbb, challengeRespOffset, certBytesOffset));
                return 200;
            }
        ));
    }

    psram_vector<uint8_t> ServerAuthenticationService::SignChallenge(const uint8_t* challenge, size_t challengeLen) {
        psram_vector<uint8_t> signature(MBEDTLS_PK_SIGNATURE_MAX_SIZE);
        size_t signatureLen;
        ESP_LOGI(TAG, "Signing challenge");
        int err = mbedtls_pk_sign(&m_SigningKey, MBEDTLS_MD_SHA256, challenge, challengeLen, signature.data(), signature.size(), &signatureLen, mbedtls_ctr_drbg_random, &m_CtrDrbg);
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to sign challenge: -0x%04X", -err);
            return {};
        }
        ESP_LOGI(TAG, "Challenge signed successfully, signature length: %d", signatureLen);
        signature.resize(signatureLen);
        return signature;
    }
}