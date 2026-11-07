package cz.spojenka.android.util;

import android.content.Context;
import android.net.Uri;
import android.os.LocaleList;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.regex.Pattern;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

public class TextUtils {

    /**
     * Capitalizes the first letter of a string.
     *
     * @param str The string to capitalize
     * @return The capitalized string, or the original string if it is null or empty
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Un-capitalizes the first letter of a string.
     * <p>
     * In situations where the string has a prefix longer than 1 that is all-uppercase,
     * the prefix is left as is. This is to prevent un-capitalizing abbreviations like FBI or WC.
     *
     * @param str The string to un-capitalize
     * @return The uncapitalized string, or the original string if it is null or empty
     */
    public static String uncapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        if (str.length() > 1) {
            if (Character.isUpperCase(str.charAt(0)) && Character.isUpperCase(str.charAt(1))) {
                // Do not uncapitalize if the first two characters are uppercase, commonly used for abbreviations (FBI, WC, ...)
                return str;
            }
        }
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }

    /**
     * Implementation of the infamous leftpad function, which pads a string from the left
     * with a character to reach a certain length.
     * If the string is already the same length or longer than the desired length, it is returned as is.
     * <p>
     * Example: {@code leftPad("123", 5, '0') -> "00123"}
     * <p>
     * In case anyone is wondering why not use a library function for this (such as Apache Commons),
     * see <a href="https://en.wikipedia.org/wiki/Npm_left-pad_incident">the left-pad incident</a>.
     *
     * @param str       The string to pad
     * @param maxLength The desired length of the string
     * @param chr       The character to pad with
     * @return The padded string
     */
    public static String leftPad(@NonNull String str, int maxLength, char chr) {
        if (str.length() >= maxLength) {
            return str;
        }
        return String.valueOf(chr).repeat(maxLength - str.length()) + str;
    }

    /**
     * Join a list of {@link CharSequence}s together, ignoring empty ({@link android.text.TextUtils#isEmpty(CharSequence)})
     * elements and preserving spans if any of the elements are {@link Spanned}.
     *
     * @param delimiter The delimiter to insert between elements
     * @param elements  The elements to join
     * @return The joined {@link CharSequence}.
     */
    public static CharSequence join(@NonNull CharSequence delimiter, CharSequence... elements) {
        return join(delimiter, Arrays.asList(elements)); //List.of does not take nulls... that's so dumb
    }

    /**
     * Join an {@link Iterable} of {@link CharSequence}s together, ignoring empty ({@link android.text.TextUtils#isEmpty(CharSequence)})
     * elements and preserving spans if any of the elements are {@link Spanned}.
     *
     * @param delimiter The delimiter to insert between elements
     * @param elements  The elements to join
     * @return The joined {@link CharSequence}.
     */
    public static CharSequence join(@NonNull CharSequence delimiter, Iterable<? extends CharSequence> elements) {
        boolean hasSpanned = false;
        for (var cs : elements) {
            if (cs instanceof Spanned) {
                hasSpanned = true;
                break;
            }
        }
        if (!hasSpanned) {
            StringJoiner joiner = new StringJoiner(delimiter);
            for (var cs : elements) {
                if (!android.text.TextUtils.isEmpty(cs)) {
                    joiner.add(cs);
                }
            }
            return joiner.toString();
        } else {
            SpannableStringBuilder builder = new SpannableStringBuilder();
            boolean firstElem = true;
            for (var cs : elements) {
                if (!android.text.TextUtils.isEmpty(cs)) {
                    if (firstElem) {
                        firstElem = false;
                    } else {
                        builder.append(delimiter);
                    }
                    builder.append(cs);
                }
            }
            return SpannableString.valueOf(builder);
        }
    }

    /**
     * Create a {@link SpannableString} with a specific style applied to the whole text.
     *
     * @param value The text to style
     * @param style The style to apply, see {@link StyleSpan}
     * @return The styled text
     */
    public static CharSequence createStyledText(String value, int style) {
        SpannableString spannableString = SpannableString.valueOf(value);
        spannableString.setSpan(new StyleSpan(style), 0, value.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannableString;
    }

    /**
     * Get the color of the first character in a {@link CharSequence}.
     *
     * @param text         The text to check
     * @param defaultValue Value to return if no color is found
     * @return The color of the first character, or the default value if the text is not styled or there is no color information for the first character.
     */
    public static int getColorAtStart(CharSequence text, @ColorInt int defaultValue) {
        if (text instanceof Spanned spanned && text.length() > 0) {
            ForegroundColorSpan[] spans = spanned.getSpans(0, 1, ForegroundColorSpan.class);
            for (ForegroundColorSpan span : spans) {
                return span.getForegroundColor();
            }
        }
        return defaultValue;
    }

    private static final Pattern NORMALIZE_ACCENTS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    /**
     * Remove accents (diacritical marks) from a string.
     *
     * @param str The string to remove accents from
     * @return The string without accents
     */
    public static String removeAccents(String str) {
        return NORMALIZE_ACCENTS_PATTERN.matcher(Normalizer.normalize(str, Normalizer.Form.NFD)).replaceAll("");
    }

    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9 ]");

    /**
     * Remove special characters from a string. Special characters are defined as anything that is not an ASCII letter, a digit or a space.
     *
     * @param str The string to remove special characters from
     * @return The string without special characters
     */
    public static String removeSpecialChars(String str) {
        return SPECIAL_CHARS_PATTERN.matcher(str).replaceAll("");
    }

    /**
     * Create a {@link Spannable} with a specific color applied to the whole text.
     *
     * @param str   The text
     * @param color The color to apply
     * @return
     */
    public static Spannable colorString(String str, @ColorInt int color) {
        SpannableString spannable = new SpannableString(str);
        spannable.setSpan(new ForegroundColorSpan(color), 0, str.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    public static Spannable uriString(String str, Uri uri) {
        return uriString(str, uri.toString());
    }

    public static Spannable uriString(String str, String uri) {
        SpannableString spannable = new SpannableString(str);
        spannable.setSpan(new URLSpan(uri), 0, str.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    /**
     * Get localized text based on the preferred locale list of the device.
     * The user's preferred locales are checked in order, and the text for the first matching locale is returned.
     * If no matching locale is found, the text for the default language is returned.
     *
     * @param context     Context
     * @param defaultLang Default language code, e.g. "en". 'getForLang' must be able to return a value for this language.
     * @param getForLang  Function that takes a language code (e.g. "en", "fr", "de") or a language tag (e.g. "en-US", "fr-FR")
     *                    and returns the corresponding localized text, or null if no text is available for that language.
     * @return The localized text.
     */
    public static String getLocalizedText(Context context, String defaultLang, Function<String, String> getForLang) {
        LocaleList locales = context.getResources().getConfiguration().getLocales();
        for (int i = 0; i < locales.size(); i++) {
            Locale locale = locales.get(i);
            String text = getForLang.apply(locale.toLanguageTag());
            if (text != null) {
                return text;
            }
            text = getForLang.apply(locale.getLanguage());
            if (text != null) {
                return text;
            }
        }
        return getForLang.apply(defaultLang);
    }
}
