#include "lwt_TicketSignatureVerifier.h"
#include "esp_log.h"

namespace lwt {

    static constexpr const char* TAG = "TicketSignatureVerifier";

    void TicketSignatureVerifier::RegisterPublicKey(uint32_t keyId, const std::span<const uint8_t>& publicKeyPem) {
        mbedtls_pk_context pk;
        mbedtls_pk_init(&pk);
        int rc = mbedtls_pk_parse_public_key(&pk, publicKeyPem.data(), publicKeyPem.size());
        if (rc != 0) {
            mbedtls_pk_free(&pk);
            ESP_LOGE(TAG, "Failed to parse public key ID: %u", keyId);
        }
        else {
            m_PublicKeys[keyId] = std::move(pk);
        }
    }

    bool TicketSignatureVerifier::VerifySignature(const std::span<const uint8_t>& digest, mbedtls_md_type_t digestType, const std::span<const uint8_t>& signature, uint32_t keyId) {
        auto it = m_PublicKeys.find(keyId);
        if (it == m_PublicKeys.end()) {
            ESP_LOGW(TAG, "Public key ID not found: %u", keyId);
            return false; // Key not found
        }

        mbedtls_pk_context& pk = it->second;

        int rc = mbedtls_pk_verify(&pk, digestType, digest.data(), digest.size(), signature.data(), signature.size());
        if (rc != 0) {
            ESP_LOGW(TAG, "Signature verification failed for key ID: %u, result: %d", keyId, rc);
        }
        return rc == 0; // Return true if verification succeeded
    }
}