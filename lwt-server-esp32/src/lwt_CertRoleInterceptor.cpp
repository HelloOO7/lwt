#include "lwt_CertRoleInterceptor.h"

#include <cstring>
#include "esp_log.h"
#include <utility>
#include "lwt_CertRole.h"

namespace lwt {

    static constexpr const char* TAG = "CertRoleInterceptor";

    lwtp::SocketSession::Tag CERT_ROLE_MASK_TAG{};

    void CertRoleInterceptor::HandleClientCertificate(lwtp::SocketSession& session, lwdn::TLSContext& tlsContext, const mbedtls_x509_crt* clientCert)
    {
        CertRole roleMask = CertRole::NONE;

        if (clientCert) {
            roleMask = CertRoleUtil::ExtractRolesFromCert(clientCert);
            ESP_LOGI(TAG, "Client authenticated with roles: %d", roleMask);
        }

        session.SetTag(&CERT_ROLE_MASK_TAG, std::to_underlying(roleMask));

        unsigned char roleMaskBytes[sizeof(roleMask)];
        memcpy(roleMaskBytes, &roleMask, sizeof(roleMask));
        tlsContext.AddTicketExtraData(roleMaskBytes);
    }

    void CertRoleInterceptor::HandleSessionResumption(lwtp::SocketSession& session, lwdn::TLSContext& tlsContext)
    {
        if (session.HasTag(&CERT_ROLE_MASK_TAG)) {
            // got certificate; no need to restore from session ticket
            return;
        }

        CertRole roleMask = CertRole::NONE;

        auto extraData = tlsContext.GetTicketExtraData();
        if (extraData.size() == sizeof(roleMask)) {
            memcpy(&roleMask, extraData.data(), sizeof(roleMask));

            session.SetTag(&CERT_ROLE_MASK_TAG, std::to_underlying(roleMask));
            ESP_LOGI(TAG, "Restored role mask %d from session ticket extra data", roleMask);
        }
        else {
            ESP_LOGW(TAG, "No role mask in session ticket extra data (size=%zu)", extraData.size());
        }
    }
}