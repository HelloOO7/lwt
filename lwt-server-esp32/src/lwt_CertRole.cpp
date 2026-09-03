#include "lwt_CertRole.h"

// header does not have extern "C" so it throws undefined reference in C++ code
extern "C" {
#include "mbedtls/oid.h"
}
#include <cstring>
#include "esp_log.h"

namespace lwt {

    static constexpr const char* TAG = "CertRole";

    CertRole CertRoleUtil::ExtractRolesFromCert(const Certificate& cert) {
        return ExtractRolesFromCert((const mbedtls_x509_crt*)cert);
    }

    CertRole CertRoleUtil::ExtractRolesFromCert(const mbedtls_x509_crt* clientCert) {
        CertRole roleMask = CertRole::NONE;

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

        return roleMask;
    }

    CertRole CertRoleUtil::ParseCertRole(const std::string& role)
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

    bool CertRoleUtil::CheckAnyRole(CertRole held, CertRole wanted) {
        return (wanted & held) != CertRole::NONE;
    }

    bool CertRoleUtil::CheckAllRoles(CertRole held, CertRole wanted) {
        return (wanted & held) == wanted;
    }
}