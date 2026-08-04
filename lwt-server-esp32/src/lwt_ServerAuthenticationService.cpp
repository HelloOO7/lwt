#include "lwt_ServerAuthenticationService.h"

#include "operations_generated.h"
#include "server_authentication_generated.h"
#include "esp_timer.h"
#include "esp_netif.h"
#include "esp_log.h"
#include "lwt_CryptoTypes.h"

namespace lwt {

    static constexpr const char* TAG = "ServerAuthenticationService";

    static constexpr const char* CHALLENGE_SALT = "LwtServerAuthentication";

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
        psram_vector<uint8_t> saltedChallenge;
        saltedChallenge.reserve(strlen(CHALLENGE_SALT) + challengeLen);
        saltedChallenge.insert(saltedChallenge.end(), CHALLENGE_SALT, CHALLENGE_SALT + strlen(CHALLENGE_SALT));
        saltedChallenge.insert(saltedChallenge.end(), challenge, challenge + challengeLen);

        SHA256Hash saltedChallengeHash;

        int err = mbedtls_sha256(saltedChallenge.data(), saltedChallenge.size(), saltedChallengeHash.data(), false);
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to compute SHA-256 hash of challenge: -0x%04X", -err);
            return {};
        }

        psram_vector<uint8_t> signature(MBEDTLS_PK_SIGNATURE_MAX_SIZE);
        size_t signatureLen;
        ESP_LOGI(TAG, "Signing challenge");
        err = mbedtls_pk_sign(&m_SigningKey, MBEDTLS_MD_SHA256, saltedChallengeHash.data(), saltedChallengeHash.size(), signature.data(), signature.size(), &signatureLen, mbedtls_ctr_drbg_random, &m_CtrDrbg);
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to sign challenge: -0x%04X", -err);
            return {};
        }
        ESP_LOGI(TAG, "Challenge signed successfully, signature length: %d", signatureLen);
        signature.resize(signatureLen);
        return signature;
    }
}