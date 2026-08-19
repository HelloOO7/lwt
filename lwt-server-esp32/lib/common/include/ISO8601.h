#pragma once

#include <cstdint>
#include <string>
#include <cstring>
#include <ctime>

struct LocalTime {
    uint8_t hour{ 0 };
    uint8_t minute{ 0 };
    uint8_t second{ 0 };
    uint32_t nanosecond{ 0 };

    inline static LocalTime parse(const char* str, const char** endPtr = nullptr) {
        LocalTime lt;
        int numRead;
        sscanf(str, "%2hhu:%2hhu%n", &lt.hour, &lt.minute, &numRead);
        if (str[numRead] == ':') {
            int numRead2;
            sscanf(str + numRead + 1, "%2hhu%n", &lt.second, &numRead2);
            numRead += 1 + numRead2;
            if (str[numRead] == '.') {
                int numRead3;
                sscanf(str + numRead + 1, "%9lu%n", &lt.nanosecond, &numRead3);
                numRead += 1 + numRead3;
            }
        }
        if (endPtr) {
            *endPtr = str + numRead;
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

    inline std::string to_string() const {
        if (second > 0) {
            if (nanosecond > 0) {
                char buffer[24]; // HH:MM:SS.nnnnnnnnn + null terminator
                snprintf(buffer, sizeof(buffer), "%02hhu:%02hhu:%02hhu.%09lu", hour, minute, second, nanosecond);
                return std::string(buffer);
            }
            else {
                char buffer[12]; // HH:MM:SS + null terminator
                snprintf(buffer, sizeof(buffer), "%02hhu:%02hhu:%02hhu", hour, minute, second);
                return std::string(buffer);
            }
        }
        else {
            char buffer[6]; // HH:MM + null terminator
            snprintf(buffer, sizeof(buffer), "%02hhu:%02hhu", hour, minute);
            return std::string(buffer);
        }
    }
};

struct LocalDate {
    uint16_t year{ 1970 };
    uint8_t month{ 1 };
    uint8_t day{ 1 };

    inline static LocalDate parse(const char* str, const char** endPtr = nullptr) {
        LocalDate ld;
        int numRead;
        sscanf(str, "%4hu-%2hhu-%2hhu%n", &ld.year, &ld.month, &ld.day, &numRead);
        if (endPtr) {
            *endPtr = str + numRead;
        }
        return ld;
    }

    inline static LocalDate parse(const std::string& str) {
        return parse(str.c_str());
    }

    inline std::string to_string() const {
        char buffer[11]; // YYYY-MM-DD + null terminator
        snprintf(buffer, sizeof(buffer), "%04hu-%02hhu-%02hhu", year, month, day);
        return std::string(buffer);
    }
};

struct LocalDateTime {
    LocalDate date;
    LocalTime time;

    inline static LocalDateTime parse(const char* str, const char** endPtr = nullptr) {
        LocalDateTime ldt;
        ldt.date = LocalDate::parse(str, &str);
        char* timePart = strchr(str, 'T');
        if (timePart) {
            ldt.time = LocalTime::parse(timePart + 1, &str);
        }
        if (endPtr) {
            *endPtr = str;
        }
        return ldt;
    }

    inline static LocalDateTime parse(const std::string& str) {
        return parse(str.c_str());
    }

    inline static LocalDateTime of_epoch_seconds(int64_t epochSeconds) {
        std::time_t t = static_cast<std::time_t>(epochSeconds);
        std::tm* tmPtr = std::gmtime(&t);
        LocalDateTime ldt;
        ldt.date.year = (uint16_t)(tmPtr->tm_year + 1900);
        ldt.date.month = (uint8_t)(tmPtr->tm_mon + 1);
        ldt.date.day = (uint8_t)(tmPtr->tm_mday);
        ldt.time.hour = (uint8_t)(tmPtr->tm_hour);
        ldt.time.minute = (uint8_t)(tmPtr->tm_min);
        ldt.time.second = (uint8_t)(tmPtr->tm_sec);
        return ldt;
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

    inline std::string to_string() const {
        return date.to_string() + "T" + time.to_string();
    }
};

struct OffsetDateTime {
    LocalDateTime date_time;
    int32_t offset_seconds{ 0 };

    inline static OffsetDateTime of(const LocalDateTime& ldt, int32_t offsetSec) {
        OffsetDateTime odt;
        odt.date_time = ldt;
        odt.offset_seconds = offsetSec;
        return odt;
    }

    inline static OffsetDateTime parse(const char* str, const char** endPtr = nullptr) {
        OffsetDateTime odt;
        odt.date_time = LocalDateTime::parse(str, &str);
        if (*str == 'Z') {
            odt.offset_seconds = 0;
            str++;
        }
        else if (*str == '+' || *str == '-') {
            int sign = (*str == '+') ? 1 : -1;
            str++;
            int hours, minutes;
            int numRead;
            // can be either 02:00 or 0200
            if (sscanf(str, "%2d:%2d%n", &hours, &minutes, &numRead) == 2) {
                odt.offset_seconds = sign * (hours * 3600 + minutes * 60);
            }
            else if (sscanf(str, "%2d%2d%n", &hours, &minutes, &numRead) == 2) {
                odt.offset_seconds = sign * (hours * 3600 + minutes * 60);
            }
            str += numRead; // Move past the offset
        }
        if (endPtr) {
            *endPtr = str;
        }
        return odt;
    }

    inline static OffsetDateTime parse(const std::string& str) {
        return parse(str.c_str());
    }

    inline std::string to_string() const {
        std::string result = date_time.to_string();
        if (offset_seconds == 0) {
            result += "Z";
        }
        else {
            int totalMinutes = offset_seconds / 60;
            int hours = totalMinutes / 60;
            int minutes = std::abs(totalMinutes % 60);
            char buffer[11]; // +HH:MM or -HH:MM + null terminator
            snprintf(buffer, sizeof(buffer), "%+03d:%02d", hours, minutes);
            result += buffer;
        }
        return result;
    }
};