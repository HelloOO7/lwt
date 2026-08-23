#include "lwt_ServerAuthenticationService.h"

#include "operations_generated.h"
#include "server_authentication_generated.h"
#include "esp_timer.h"
#include "esp_netif.h"
#include "esp_log.h"
#include "CryptoTypes.h"
#include "mbedtls/md.h"

namespace lwt {

    static constexpr const char* TAG = "ServerAuthenticationService";

    static constexpr const char* CHALLENGE_SALT = "LwtServerAuthentication";

    ServerAuthenticationService::ServerAuthenticationService(uint8_t* certString, DigitalSignature& signingKey) :
        m_CertString{ certString },
        m_SigningKey{ signingKey }
    {
    }

    void ServerAuthenticationService::Register(ServiceRegistry& registry) {
        registry.RegisterServiceCallback(Operation_AuthenticateServer, ApplicationServer::CreateOperationServiceFunc<ServerAuthenticationRequest>(
            [this](const ServerAuthenticationRequest& req, flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                auto signedChallenge = SignChallenge(flatbuffers::make_span(req.challenge()));
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

    psram_vector<uint8_t> ServerAuthenticationService::SignChallenge(const ByteSpan& challenge) {
        psram_vector<uint8_t> saltedChallenge;
        saltedChallenge.reserve(strlen(CHALLENGE_SALT) + challenge.size());
        saltedChallenge.insert(saltedChallenge.end(), CHALLENGE_SALT, CHALLENGE_SALT + strlen(CHALLENGE_SALT));
        saltedChallenge.insert(saltedChallenge.end(), challenge.data(), challenge.data() + challenge.size());

        psram_vector<uint8_t> signature;
        int res = m_SigningKey.Sign(saltedChallenge, &signature, MBEDTLS_MD_SHA256);
        if (res != 0) {
            ESP_LOGE(TAG, "Failed to sign challenge, error code: %d", res);
            return {};
        }

        ESP_LOGI(TAG, "Challenge signed successfully, signature length: %d", signature.size());
        return signature;
    }
}