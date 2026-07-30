#pragma once

#include <span>
#include <cstdint>
#include <map>
#include "mbedtls/pk.h"

namespace lwt {

    class TicketSignatureVerifier {
    private:
        std::map<uint32_t, mbedtls_pk_context> m_PublicKeys;

    public:
        void RegisterPublicKey(uint32_t keyId, const std::span<const uint8_t>& publicKeyPem);

        bool VerifySignature(const std::span<const uint8_t>& digest, mbedtls_md_type_t digestType, const std::span<const uint8_t>& signature, uint32_t keyId);
    };
}