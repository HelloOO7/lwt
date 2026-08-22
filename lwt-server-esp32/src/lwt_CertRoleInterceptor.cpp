#include "lwt_CertRoleInterceptor.h"

// header does not have extern "C" so it throws undefined reference in C++ code
extern "C" {
#include "mbedtls/oid.h"
}
#include <cstring>
#include "esp_log.h"
#include <utility>

namespace lwt {

    static constexpr const char* TAG = "CertRoleInterceptor";

    lwtp::SocketSession::Tag CERT_ROLE_MASK_TAG{};

    void CertRoleInterceptor::HandleClientCertificate(lwtp::SocketSession& session, lwdn::TLSContext& tlsContext, const mbedtls_x509_crt* clientCert)
    {
        CertRole roleMask = CertRole::NONE;

        if (!clientCert) {
            return;
        }

        for (const mbedtls_x509_name* name = &clientCert->subject; !!name; name = name->next) {
            char oid[16];

            int ret = mbedtls_oid_get_numeric_string(oid, sizeof(oid), &name->oid);

            if (ret < 0) {
                continue;
            }

            if (strcmp(oid, "2.5.4.72") == 0)
            {
                auto role = ParseCertRole(std::string((const char*)name->val.p, name->val.len));
                if (role != CertRole::NONE) {
                    roleMask = roleMask | role;
                }
                else {
                    ESP_LOGW(TAG, "Unknown role in certificate: %.*s", (int)name->val.len, name->val.p);
                }
            }
        }

        ESP_LOGI(TAG, "Client authenticated with roles: %d", roleMask);

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

    CertRole CertRoleInterceptor::ParseCertRole(const std::string& role)
    {
        if (role == "LWT_DEVICE") {
            return CertRole::LWT_DEVICE;
        }
        else if (role == "TICKET_INSPECTOR") {
            return CertRole::TICKET_INSPECTOR;
        }
        else {
            return CertRole::NONE;
        }
    }
}