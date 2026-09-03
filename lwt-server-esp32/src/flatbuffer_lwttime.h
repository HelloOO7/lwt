#pragma once

#include "ISO8601.h"
#include "datetime_generated.h"

inline LocalDateTime FlatLwtToIso(const lwt::LwtLocalDateTime& dt) {
    return LocalDateTime::of_utc_epoch_seconds(dt.local_instant());
}

inline lwt::LwtOffsetDateTime IsoToFlatLwt(const OffsetDateTime& dt) {
    return lwt::LwtOffsetDateTime(lwt::LwtLocalDateTime(dt.date_time.to_utc_epoch_seconds()), dt.offset_seconds);
}

    