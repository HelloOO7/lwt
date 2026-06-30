#pragma once

#include <cstdint>
#include <string>
#include <cstring>
#include <ctime>

struct LocalTime {
    uint8_t hour{ 0 };
    uint8_t minute{ 0 };
    uint8_t second{ 0 };

    inline static LocalTime parse(const char* str) {
        LocalTime lt;
        sscanf(str, "%2hhu:%2hhu", &lt.hour, &lt.minute);
        if (str[5] == ':') {
            sscanf(str + 6, "%2hhu", &lt.second);
        }
        return lt;
    }

    inline static LocalTime parse(const std::string& str) {
        return parse(str.c_str());
    }

    inline size_t to_second_of_day() const {
        return hour * 3600 + minute * 60 + second;
    }

    inline size_t to_minute_of_day() const {
        return hour * 60 + minute;
    }
};

struct LocalDate {
    uint16_t year{ 1970 };
    uint8_t month{ 1 };
    uint8_t day{ 1 };

    inline static LocalDate parse(const char* str) {
        LocalDate ld;
        sscanf(str, "%4hu-%2hhu-%2hhu", &ld.year, &ld.month, &ld.day);
        return ld;
    }

    inline static LocalDate parse(const std::string& str) {
        return parse(str.c_str());
    }
};

struct LocalDateTime {
    LocalDate date;
    LocalTime time;

    inline static LocalDateTime parse(const char* str) {
        LocalDateTime ldt;
        ldt.date = LocalDate::parse(str);
        char* timePart = strchr(str, 'T');
        if (timePart) {
            ldt.time = LocalTime::parse(timePart + 1);
        }
        return ldt;
    }

    inline static LocalDateTime parse(const std::string& str) {
        return parse(str.c_str());
    }

    inline int64_t to_epoch_seconds() const {
        std::tm tm = {};
        tm.tm_year = date.year - 1900;
        tm.tm_mon = date.month - 1;
        tm.tm_mday = date.day;
        tm.tm_hour = time.hour;
        tm.tm_min = time.minute;
        tm.tm_sec = time.second;
        return static_cast<int64_t>(std::mktime(&tm));
    }
};
