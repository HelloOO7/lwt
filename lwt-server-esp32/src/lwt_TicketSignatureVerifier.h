#pragma once

#include <span>
#include <cstdint>
#include <map>
#include <memory>
#include "DigitalSignature.h"
#include "CommonTypes.h"

namespace lwt {

    class TicketSignatureVerifier {
    private:
        std::map<uint32_t, std::unique_ptr<DigitalSignature>> m_PublicKeys;

    public:
        void RegisterPublicKey(uint32_t keyId, const ByteSpan& publicKeyPem);

        bool VerifyHashSignature(const ByteSpan& digest, mbedtls_md_type_t digestType, const ByteSpan& signature, uint32_t keyId);
    };
}