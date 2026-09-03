#pragma once

#include "EnumBitflags.h"
#include "Certificate.h"

namespace lwt {

    enum class CertRole : int {
        NONE = 0,
        LWT_DEVICE = (1 << 0),
        TICKET_INSPECTOR = (1 << 1),
    };

    DEFINE_ENUM_FLAG_OPERATORS(CertRole);

    class CertRoleUtil {
    public:
        static CertRole ExtractRolesFromCert(const Certificate& cert);
        static CertRole ExtractRolesFromCert(const mbedtls_x509_crt* cert);

        static CertRole ParseCertRole(const std::string& role);

        static bool CheckAnyRole(CertRole held, CertRole wanted);
        static bool CheckAllRoles(CertRole held, CertRole wanted);
    };
}