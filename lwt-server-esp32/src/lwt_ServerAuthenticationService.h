#pragma once

#include "lwt_ApplicationServer.h"
#include "lwt_ServiceRegistry.h"
#include "mbedtls/pk.h"
#include "PSRAMContainers.h"
#include "CommonTypes.h"
#include "DigitalSignature.h"
#include "Certificate.h"

namespace lwt {

    class ServerAuthenticationService {
    private:
        Certificate& m_DeviceCert;
        DigitalSignature& m_SigningKey;

    public:
        ServerAuthenticationService(Certificate& deviceCert, DigitalSignature& signingKey);

        void Register(ServiceRegistry& registry);

    private:
        ByteVector SignChallenge(const ByteSpan& challenge);
    };
}