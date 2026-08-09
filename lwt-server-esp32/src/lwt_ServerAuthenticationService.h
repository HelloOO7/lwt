#pragma once

#include "lwt_ApplicationServer.h"
#include "lwt_ServiceRegistry.h"
#include "mbedtls/pk.h"
#include "PSRAMContainers.h"

namespace lwt {

    class ServerAuthenticationService {
    private:
        uint8_t* m_CertString;
        mbedtls_pk_context& m_SigningKey;

    public:
        ServerAuthenticationService(uint8_t* certString, mbedtls_pk_context& signingKey);

        void Register(ServiceRegistry& registry);

    private:
        psram_vector<uint8_t> SignChallenge(const uint8_t* challenge, size_t challengeLen);
    };
}