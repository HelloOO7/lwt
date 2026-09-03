#pragma once

#include <array>
#include <cstdint>
#include <string_view>

class UUID : public std::array<uint8_t, 16> {
public:
    using std::array<uint8_t, 16>::array;

    static UUID Nil();

    static UUID V4();
    static UUID V7();
    static UUID Parse(const std::string_view& str);

    uint8_t GetVersion() const;
    uint8_t GetVariant() const;

    std::string ToString() const;
private:
    void SetVersion(uint8_t version);
    void SetVariant(uint8_t variant);
};