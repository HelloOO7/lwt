#include "lwt_TicketSignatureVerifier.h"
#include "esp_log.h"
#include "CryptoTypes.h"

namespace lwt {

    static constexpr const char* TAG = "TicketSignatureVerifier";

    void TicketSignatureVerifier::RegisterPublicKey(uint32_t keyId, const ByteSpan& publicKeyPem) {
        m_PublicKeys[keyId] = std::make_unique<DigitalSignature>(publicKeyPem, DigitalSignature::KeyUsage::VERIFY);
    }

    bool TicketSignatureVerifier::VerifyHashSignature(const ByteSpan& digest, mbedtls_md_type_t digestType, const ByteSpan& signature, uint32_t keyId) {
        auto it = m_PublicKeys.find(keyId);
        if (it == m_PublicKeys.end()) {
            ESP_LOGW(TAG, "Public key ID not found: %u", keyId);
            return false; // Key not found
        }

        auto&& signatureVerifier = it->second;

        int rc = signatureVerifier->VerifyDigest(digest, digestType, signature);
        if (rc != 0) {
            ESP_LOGW(TAG, "Signature verification failed for key ID: %u, result: %d", keyId, rc);
        }
        return rc == 0; // Return true if verification succeeded
    }
}