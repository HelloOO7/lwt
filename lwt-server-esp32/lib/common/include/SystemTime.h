#pragma once

#include <sys/time.h>
#include "esp_timer.h"

class SystemTime {
public:
    inline static int64_t EpochMillis() {
        struct timeval tv;
        gettimeofday(&tv, nullptr);
        return static_cast<int64_t>(tv.tv_sec) * 1000 + static_cast<int64_t>(tv.tv_usec) / 1000;
    }

    inline static int64_t EpochSeconds() {
        return EpochMillis() / 1000;
    }

    inline static int64_t UptimeMicros() {
        return esp_timer_get_time();
    }

    inline static int64_t UptimeMillis() {
        return UptimeMicros() / 1000;
    }

    inline static int64_t UptimeSeconds() {
        return UptimeMillis() / 1000;
    }
};