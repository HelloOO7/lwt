#pragma once

#include "EnumBitflags.h"

namespace lwt {

    enum class CertRole : int {
        NONE = 0,
        LWT_DEVICE = (1 << 0),
        TICKET_INSPECTOR = (1 << 1),
    };

    DEFINE_ENUM_FLAG_OPERATORS(CertRole);
}