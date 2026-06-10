#pragma once

#include "lwt_ApplicationServer.h"
#include "lwt_ServiceRegistry.h"
#include "mbedtls/pk.h"
#include "mbedtls/ctr_drbg.h"
#include "PSRAMContainers.h"

namespace lwt {

    class ServerAuthenticationService {
    private:
        uint8_t* m_CertString;
        mbedtls_pk_context& m_SigningKey;
        mbedtls_ctr_drbg_context& m_CtrDrbg;

    public:
        ServerAuthenticationService(uint8_t* certString, mbedtls_pk_context& signingKey, mbedtls_ctr_drbg_context& ctrDrbg);

        void Register(ServiceRegistry& registry);

    private:
        psram_vector<uint8_t> SignChallenge(const uint8_t* challenge, size_t challengeLen);
    };
}