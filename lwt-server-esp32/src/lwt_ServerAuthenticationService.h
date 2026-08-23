#pragma once

#include "lwt_ApplicationServer.h"
#include "lwt_ServiceRegistry.h"
#include "mbedtls/pk.h"
#include "PSRAMContainers.h"
#include "CommonTypes.h"
#include "DigitalSignature.h"

namespace lwt {

    class ServerAuthenticationService {
    private:
        uint8_t* m_CertString;
        DigitalSignature& m_SigningKey;

    public:
        ServerAuthenticationService(uint8_t* certString, DigitalSignature& signingKey);

        void Register(ServiceRegistry& registry);

    private:
        psram_vector<uint8_t> SignChallenge(const ByteSpan& challenge);
    };
}