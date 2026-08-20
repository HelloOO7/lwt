#pragma once

#include <cctype>
#include <locale>
#include <string>
#include <vector>

// ChatGPT

template <typename StringT, typename IsSpacePred>
StringT str_trim(const StringT& s, IsSpacePred is_space) {
    using size_type = typename StringT::size_type;

    if (s.empty())
        return s;

    size_type start = 0;
    size_type end = s.size();

    while (start < end && is_space(s[start])) {
        ++start;
    }
    while (end > start && is_space(s[end - 1])) {
        --end;
    }

    return s.substr(start, end - start);
}

template <typename StringT, typename IsSpacePred>
StringT str_normalize_space(const StringT& s, IsSpacePred is_space) {
    StringT result {};
    result.reserve(s.size());

    bool in_space = false;
    for (auto ch : s) {
        if (is_space(ch)) {
            if (!in_space) {
                result.push_back(ch); // emit exactly one space
                in_space = true;
            }
        } else {
            result.push_back(ch);
            in_space = false;
        }
    }

    return str_trim(result, is_space);
}

template <typename StringT>
StringT str_replace(const StringT& input, const StringT& from, const StringT& to) {
    if (from.empty())
        return input; // avoid infinite loop

    StringT result {};
    result.reserve(input.size());

    size_t pos = 0;
    size_t found;
    while ((found = input.find(from, pos)) != StringT::npos) {
        result.append(input, pos, found - pos); // append up to match
        result.append(to); // append replacement
        pos = found + from.size(); // move past match
    }
    result.append(input, pos, input.size() - pos); // append the rest
    return result;
}

template <typename StringT>
bool str_contains(const StringT& s, const StringT& sub) {
    return s.find(sub) != StringT::npos;
}

template <typename StringT>
bool str_contains(const StringT& s, typename StringT::value_type ch) {
    return s.find(ch) != StringT::npos;
}

template <typename StringT>
std::vector<StringT> str_split(const StringT& s, typename StringT::value_type delimiter) {
    std::vector<StringT> result;
    StringT              current {};

    for (auto ch : s) {
        if (ch == delimiter) {
            result.push_back(std::move(current));
            current.clear();
        } else {
            current.push_back(ch);
        }
    }
    result.push_back(std::move(current));

    // Remove trailing empty parts (to match Java's behavior)
    while (!result.empty() && result.back().empty()) {
        result.pop_back();
    }

    return result;
}

template <typename StringT, typename It>
StringT str_join(const It& begin, const It& end, typename StringT::value_type separator) {
    StringT result;
    for (auto it = begin; it != end; ++it) {
        if (it != begin) {
            result += separator;
        }
        result += *it;
    }
    return result;
}

template <typename StringT, typename It>
StringT str_join(const It& begin, const It& end, const StringT& separator) {
    StringT result;
    for (auto it = begin; it != end; ++it) {
        if (it != begin) {
            result += separator;
        }
        result += *it;
    }
    return result;
}

// ---------- Inline wrappers for common string types ----------

// ASCII space only
inline std::string str_trim(const std::string& s) {
    return str_trim(s, [](char ch) { return ch == ' '; });
}
inline std::string str_normalize_space(const std::string& s) {
    return str_normalize_space(s, [](char ch) { return ch == ' '; });
}
inline std::string str_replace(const std::string& s, const std::string& from, const std::string& to) {
    return str_replace<std::string>(s, from, to);
}
inline bool str_contains(const std::string& s, const std::string& sub) {
    return str_contains<std::string>(s, sub);
}
inline bool str_contains(const std::string& s, char ch) {
    return str_contains<std::string>(s, ch);
}
inline std::vector<std::string> str_split(const std::string& s, char delimiter) {
    return str_split<std::string>(s, delimiter);
}
template <typename It>
inline std::string str_join(const It& begin, const It& end, char separator) {
    return str_join<std::string>(begin, end, separator);
}
template <typename It>
inline std::string str_join(const It& begin, const It& end, const std::string& separator) {
    return str_join<std::string>(begin, end, separator);
}

// wide string
inline std::wstring str_trim(const std::wstring& s) {
    return str_trim(s, [](wchar_t ch) { return ch == L' '; });
}
inline std::wstring str_normalize_space(const std::wstring& s) {
    return str_normalize_space(s, [](wchar_t ch) { return ch == L' '; });
}
inline std::wstring str_replace(const std::wstring& s, const std::wstring& from, const std::wstring& to) {
    return str_replace<std::wstring>(s, from, to);
}
inline bool str_contains(const std::wstring& s, const std::wstring& sub) {
    return str_contains<std::wstring>(s, sub);
}
inline bool str_contains(const std::wstring& s, wchar_t ch) {
    return str_contains<std::wstring>(s, ch);
}
inline std::vector<std::wstring> str_split(const std::wstring& s, wchar_t delimiter) {
    return str_split<std::wstring>(s, delimiter);
}
template <typename It>
inline std::wstring str_join(const It& begin, const It& end, wchar_t separator) {
    return str_join<std::wstring>(begin, end, separator);
}
template <typename It>
inline std::wstring str_join(const It& begin, const It& end, const std::wstring& separator) {
    return str_join<std::wstring>(begin, end, separator);
}

// UTF-16
inline std::u16string str_trim(const std::u16string& s) {
    return str_trim(s, [](char16_t ch) { return ch == u' '; });
}
inline std::u16string str_normalize_space(const std::u16string& s) {
    return str_normalize_space(s, [](char16_t ch) { return ch == u' '; });
}
inline std::u16string str_replace(const std::u16string& s, const std::u16string& from, const std::u16string& to) {
    return str_replace<std::u16string>(s, from, to);
}
inline bool str_contains(const std::u16string& s, const std::u16string& sub) {
    return str_contains<std::u16string>(s, sub);
}
inline bool str_contains(const std::u16string& s, char16_t ch) {
    return str_contains<std::u16string>(s, ch);
}
inline std::vector<std::u16string> str_split(const std::u16string& s, char16_t delimiter) {
    return str_split<std::u16string>(s, delimiter);
}
template <typename It>
inline std::u16string str_join(const It& begin, const It& end, char16_t separator) {
    return str_join<std::u16string>(begin, end, separator);
}
template <typename It>
inline std::u16string str_join(const It& begin, const It& end, const std::u16string& separator) {
    return str_join<std::u16string>(begin, end, separator);
}

// UTF-32
inline std::u32string str_trim(const std::u32string& s) {
    return str_trim(s, [](char32_t ch) { return ch == U' '; });
}
inline std::u32string str_normalize_space(const std::u32string& s) {
    return str_normalize_space(s, [](char32_t ch) { return ch == U' '; });
}
inline std::u32string str_replace(const std::u32string& s, const std::u32string& from, const std::u32string& to) {
    return str_replace<std::u32string>(s, from, to);
}
inline bool str_contains(const std::u32string& s, const std::u32string& sub) {
    return str_contains<std::u32string>(s, sub);
}
inline bool str_contains(const std::u32string& s, char32_t ch) {
    return str_contains<std::u32string>(s, ch);
}
inline std::vector<std::u32string> str_split(const std::u32string& s, char32_t delimiter) {
    return str_split<std::u32string>(s, delimiter);
}
template <typename It>
inline std::u32string str_join(const It& begin, const It& end, char32_t separator) {
    return str_join<std::u32string>(begin, end, separator);
}
template <typename It>
inline std::u32string str_join(const It& begin, const It& end, const std::u32string& separator) {
    return str_join<std::u32string>(begin, end, separator);
}
