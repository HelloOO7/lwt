package cz.spojenka.android.util;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.spojenka.android.polyfills.DurationCompat;
import cz.spojenka.lwt.demoapp.R;

/**
 * Functions for advanced formatting date and time.
 */
public class DateTimeUtils {

    /**
     * Formatter for a time of day in the format "H:mm" (e.g. "9:30").
     */
    public static final DateTimeFormatter TIME_H_MM = DateTimeFormatter.ofPattern("H:mm");
    /**
     * Formatter for a time of day in the format "HH:mm" (e.g. "09:30").
     */
    public static final DateTimeFormatter TIME_HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    /**
     * Formatter for a time of day in the format "H:mm:ss" (e.g. "9:30:15").
     */
    public static final DateTimeFormatter TIME_H_MM_SS = DateTimeFormatter.ofPattern("H:mm:ss");

    /**
     * Default time formatter, see {@link #TIME_H_MM}.
     */
    public static final DateTimeFormatter TIME_FORMATTER = TIME_H_MM;
    /**
     * Default formatter for a date within a year, which only contains the day and month as numbers (e.g. "9. 5.").
     */
    public static final DateTimeFormatter DATE_OF_YEAR_FORMATTER = DateTimeFormatter.ofPattern("d.\u00a0M.");
    /**
     * Default formatter for a full date, which contains the day, month and year (e.g. "17. 7. 2025").
     */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d.\u00a0M. yyyy");

    /**
     * Localized formatter with the {@link FormatStyle#MEDIUM} style for dates.
     */
    public static final DateTimeFormatter DATE_LOCALIZED_MEDIUM = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
    /**
     * Localized formatter with the {@link FormatStyle#SHORT} style for times.
     */
    public static final DateTimeFormatter TIME_LOCALIZED_SHORT = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

    /**
     * Formatter for a date with a weekday in the format "EEEE d. M." (e.g. "Monday 9. 5.").
     * For an abbreviated representation of weekdays, see {@link #DATE_WITH_WEEKDAY_SHORT}.
     */
    public static final DateTimeFormatter DATE_WITH_WEEKDAY = DateTimeFormatter.ofPattern("EEEE d.\u00a0M.");
    /**
     * Formatter for a date with a weekday in the format "EE d. M." (e.g. "Mon 9. 5.").
     * For a full representation of weekdays, see {@link #DATE_WITH_WEEKDAY}.
     */
    public static final DateTimeFormatter DATE_WITH_WEEKDAY_SHORT = DateTimeFormatter.ofPattern("EE d.\u00a0M.");

    /**
     * Format a local date-time to a localized string with the {@link #DATE_LOCALIZED_MEDIUM} date style and
     * {@link #TIME_LOCALIZED_SHORT} time style formatters.
     *
     * @param localDt the local date-time to format
     * @return
     */
    public static String formatDateTimeLocalized(LocalDateTime localDt) {
        return localDt.toLocalDate().format(DATE_LOCALIZED_MEDIUM) + " " + localDt.toLocalTime().format(TIME_LOCALIZED_SHORT);
    }

    /**
     * Format a date-time relative to the current moment.
     *
     * @see #formatDateTimeRelative(LocalDateTime, LocalDateTime, DateTimeFormatLayout)
     *
     * @param localDt The date-time to format
     * @param layout The layout of the formatted string (order of date and time)
     * @return
     */
    public static Spanned formatDateTimeRelative(LocalDateTime localDt, DateTimeFormatLayout layout) {
        return formatDateTimeRelative(localDt, LocalDateTime.now(), layout);
    }

    /**
     * Format a date-time relative to another given date-time.
     * The formatting rules are as follows:
     * <ol>
     *     <li>If both date-times are on the same day, the result is just the time mark.</li>
     *     <li>If both date-times are in the same year, the result is the day, month and time.</li>
     *     <li>Otherwise, the result is the day, month, year and time.</li>
     * </ol>
     *
     * @param localDt The date-time to format
     * @param asSeenFrom Time relative to which the date-time is formatted
     * @param layout The layout of the formatted string (order of date and time)
     * @return The formatted date-time as a styled string. The date will use the "small" HTML tag.
     */
    public static Spanned formatDateTimeRelative(LocalDateTime localDt, LocalDateTime asSeenFrom, DateTimeFormatLayout layout) {
        String time = TIME_FORMATTER.format(localDt);
        String html = "";
        if (localDt.toLocalDate().equals(asSeenFrom.toLocalDate())) {
            html = time;
        } else {
            String fmt = layout == DateTimeFormatLayout.DATE_THEN_TIME ? "<small>%s</small> %s" : "%s <small>%s</small>";
            String fullDateStr;
            if (localDt.getYear() == asSeenFrom.getYear()) {
                fullDateStr = DATE_OF_YEAR_FORMATTER.format(localDt);
            } else {
                fullDateStr = DATE_FORMATTER.format(localDt);
            }
            html = String.format(fmt, (Object[]) ((layout == DateTimeFormatLayout.DATE_THEN_TIME) ? new String[]{fullDateStr, time} : new String[]{time, fullDateStr}));
        }
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
    }

    /**
     * Get the default specialization of a relative date representation based on the distance from today's date.
     *
     * @param date The date
     * @param today The reference (today's) date
     * @return The specialization ({@link RelativeDateFormatSpec}).
     */
    public static RelativeDateFormatSpec getRelativeDateFormatSpec(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return RelativeDateFormatSpec.TODAY;
        } else if (today.minusDays(1).equals(date)) {
            return RelativeDateFormatSpec.YESTERDAY;
        } else if (today.plusDays(1).equals(date)) {
            return RelativeDateFormatSpec.TOMORROW;
        } else {
            return (date.getYear() == today.getYear()) ? RelativeDateFormatSpec.WEEKDAY : RelativeDateFormatSpec.DATE_FULL;
        }
    }

    /**
     * Format a date according to a {@link RelativeDateFormatSpec} decided based on the distance from today's date, in nominative case.
     *
     * @param context Context
     * @param date The date
     * @param today The reference (today's) date
     * @param weekdayStyle Style of weekday representation if {@link RelativeDateFormatSpec#WEEKDAY} is used.
     * @return The formatted date
     */
    public static String formatRelativeDate(Context context, LocalDate date, LocalDate today, WeekdayStyle weekdayStyle) {
        return formatRelativeDate(context, date, getRelativeDateFormatSpec(date, today), weekdayStyle, DateDeclension.NOMINATIVE);
    }

    /**
     * Format a date according to a {@link RelativeDateFormatSpec}.
     *
     * @param context Context
     * @param date The date
     * @param formatSpec The format specialization
     * @param weekdayStyle Style of weekday representation if {@link RelativeDateFormatSpec#WEEKDAY} is used.
     * @param declension Declension of the date
     * @return The formatted date
     */
    public static String formatRelativeDate(Context context, LocalDate date, RelativeDateFormatSpec formatSpec, WeekdayStyle weekdayStyle, DateDeclension declension) {
        if (formatSpec == RelativeDateFormatSpec.WEEKDAY && weekdayStyle == WeekdayStyle.NONE) {
            formatSpec = RelativeDateFormatSpec.DATE_FULL;
        }
        return switch (formatSpec) {
            case TODAY -> context.getString(R.string.today);
            case YESTERDAY -> context.getString(R.string.yesterday);
            case TOMORROW -> context.getString(R.string.tomorrow);
            case WEEKDAY -> {
                if (declension == DateDeclension.ACCUSATIVE) {
                    int index = date.getDayOfWeek().getValue() - 1;
                    yield InflectionUtils.inflectFromTemplate(context.getString(R.string.date_weekday_accusative_prefix), index)
                            + " "
                            + InflectionUtils.inflectFromTemplate(context.getString(R.string.date_weekday_accusative), index)
                            + " "
                            + date.format(DATE_OF_YEAR_FORMATTER);
                }
                yield date.format(weekdayStyle == WeekdayStyle.FULL ? DATE_WITH_WEEKDAY : DATE_WITH_WEEKDAY_SHORT);
            }
            case DATE_FULL -> {
                String value = date.format(DATE_FORMATTER);
                if (declension == DateDeclension.ACCUSATIVE) {
                    String prefix = context.getString(R.string.date_numeric_accusative_prefix);
                    if (!prefix.isEmpty()) {
                        yield prefix + " " + value;
                    } else {
                        yield value;
                    }
                }
                yield date.format(DATE_OF_YEAR_FORMATTER);
            }
        };
    }

    /**
     * Get the number of "clock" minutes between two date-times.
     * This is the difference in minutes that one would see on a digital clock that does not
     * show seconds. For example, both the difference between 12:15:59 and 12:16:00 and the
     * difference between 12:15:01 and 12:16:59 would be 1 "clock" minute. The function
     * supports negative differences.
     *
     * @param from The first date-time
     * @param to The second date-time
     * @return The number of "clock" minutes between the two date-times
     */
    private static int getWallClockMinutesBetween(LocalDateTime from, LocalDateTime to) {
        return getWallClockDurationBetween(from, to, ChronoUnit.MINUTES);
    }

    private static int getWallClockDurationBetween(LocalDateTime from, LocalDateTime to, ChronoUnit units) {
        return (int) units.between(from.truncatedTo(units), to.truncatedTo(units));
    }

    /**
     * Format a date-time phrase in a human-readable form. See {@link #formatDateTimePhrase(Context, LocalDateTime, RelativeFormatParams)}.
     *
     * @param context Context
     * @param localDt The date-time to format
     * @param declension The grammatical case of the date-time phrase
     * @return The formatted date-time phrase
     */
    public static String formatDateTimePhrase(Context context, LocalDateTime localDt, DateDeclension declension) {
        return formatDateTimePhrase(context, localDt, LocalDateTime.now(), declension);
    }

    /**
     * Format a date-time phrase in a human-readable form. See {@link #formatDateTimePhrase(Context, LocalDateTime, RelativeFormatParams)}.
     *
     * @param context Context
     * @param localDt The date-time to format
     * @param asSeenFrom The date-time relative to which the date-time is formatted
     * @param declension The grammatical case of the date-time phrase
     * @return The formatted date-time phrase
     */
    public static String formatDateTimePhrase(Context context, LocalDateTime localDt, LocalDateTime asSeenFrom, DateDeclension declension) {
        return formatDateTimePhrase(context, localDt, new RelativeFormatParams().withAsSeenFrom(asSeenFrom).withDeclension(declension));
    }

    /**
     * Format a date-time phrase in a human-readable form. The formatting rules are defined by the
     * {@link RelativeFormatParams} parameter.
     * <p>
     * When combining the date and time, if {@link RelativeFormatParams#withUseRelativeTimeOnlyLimit(Integer)}
     * was used and the time does not exceed the limit, only the time is returned. Otherwise, they are joined together
     * by a separator decided based on the grammatical case. The date format specialization is decided automatically
     * using {@link #getRelativeDateFormatSpec(LocalDate, LocalDate)}.
     *
     * @param context Context
     * @param localDt The date-time to format
     * @param params The formatting parameters
     * @return The formatted date-time phrase
     */
    public static String formatDateTimePhrase(Context context, LocalDateTime localDt, RelativeFormatParams params) {
        StringBuilder sb = new StringBuilder();

        boolean timeOnlyUsed = false;

        if (params.useRelativeTimeOnlyLimit != null) {
            int minutes = getWallClockMinutesBetween(params.asSeenFrom, localDt);
            if (Math.abs(minutes) <= params.useRelativeTimeOnlyLimit) {
                String timeString;
                if (params.relativeTimeSmallestUnit == ChronoUnit.MINUTES) {
                    timeString = formatTimeDifferenceMinutes(context, Duration.ofMinutes(Math.abs(minutes)));
                }
                else {
                    timeString = formatTimeDifference(context, Duration.between(params.asSeenFrom, localDt).abs(), params.relativeTimeSmallestUnit);
                }
                int truncatedDifference = getWallClockDurationBetween(params.asSeenFrom, localDt, params.relativeTimeSmallestUnit);
                if (truncatedDifference == 0) {
                    sb.append(context.getString(R.string.now));
                } else if (truncatedDifference > 0) {
                    sb.append(context.getString(R.string.in_x_time_format, timeString));
                } else {
                    sb.append(context.getString(R.string.x_ago_time_format, timeString));
                }
                timeOnlyUsed = true;
            }
        }
        if (!timeOnlyUsed) {
            timeOnlyUsed = params.useTimeOnlyCondition.test(localDt);

            if (!timeOnlyUsed) {
                RelativeDateFormatSpec dateFormatSpec = getRelativeDateFormatSpec(localDt.toLocalDate(), params.asSeenFrom.toLocalDate());
                String dateString = formatRelativeDate(context, localDt.toLocalDate(), dateFormatSpec, params.weekdayStyle, params.declension);

                sb.append(dateString);
            }

            String timeString = formatTime(localDt.toLocalTime());

            if (params.declension == DateDeclension.NOMINATIVE) {
                if (!timeOnlyUsed) {
                    sb.append(context.getString(R.string.date_clock_nominative_separator));
                }
                sb.append(timeString);
            } else if (params.declension == DateDeclension.ACCUSATIVE) {
                if (!timeOnlyUsed) {
                    sb.append(" ");
                }
                sb
                        .append(InflectionUtils.inflectFromTemplate(context.getString(R.string.date_clock_accusative_prefix), localDt.getHour()))
                        .append(" ")
                        .append(timeString);
            }
        }

        if (params.declension == DateDeclension.ACCUSATIVE && sb.length() > 0 && Character.isUpperCase(sb.charAt(0))) { //uncapitalize if accusative
            sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
        }

        return sb.toString();
    }

    /**
     * Format a time using the default format (see {@link #TIME_FORMATTER}).
     *
     * @param time The time
     * @return
     */
    public static String formatTime(LocalTime time) {
        return time.format(TIME_FORMATTER);
    }

    /**
     * Format a date using the default format (see {@link #DATE_FORMATTER}).
     *
     * @param date The date
     * @return
     */
    public static String formatDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }

    /**
     * Remove the seconds (and lower units) from a time.
     * This is the same as truncating the time to a minute.
     *
     * @param time The time, possibly null.
     * @return The time with the seconds set to zero, or null if the input was null.
     */
    public static LocalTime withSecondsZero(@Nullable LocalTime time) {
        if (time == null) {
            return null;
        }
        return time.truncatedTo(ChronoUnit.MINUTES);
    }

    /**
     * Format a time difference truncated to minutes.
     *
     * @param context Context
     * @param duration The time difference
     * @return The formatted time difference, as with {@link #formatTimeDifference(Context, Duration, ChronoUnit)}.
     */
    public static String formatTimeDifferenceMinutes(Context context, Duration duration) {
        return formatTimeDifference(context, duration, ChronoUnit.MINUTES);
    }

    /**
     * Format a time difference in a human-readable form.
     * The duration will be expanded into individual day, hour, minute and second components,
     * non-zero of which down to a specified smallest unit will be included in the result.
     *
     * @param context Context
     * @param duration The time difference
     * @param smallestUnit The smallest unit to include in the result. Smaller differences will be omitted.
     * @return
     */
    public static String formatTimeDifference(Context context, Duration duration, ChronoUnit smallestUnit) {
        List<String> texts = new ArrayList<>(3);
        int days = (int) DurationCompat.toDaysPart(duration);
        int hours = DurationCompat.toHoursPart(duration);
        int minutes = DurationCompat.toMinutesPart(duration);
        int seconds = DurationCompat.toSecondsPart(duration);

        boolean allZero = days == 0 && hours == 0 && minutes == 0 && seconds == 0;

        if (days > 0 || (allZero && smallestUnit == ChronoUnit.DAYS)) {
            texts.add(context.getResources().getQuantityString(R.plurals.day_short, days, days));
        }

        //you can never have enough nesting :)
        if (smallestUnit != ChronoUnit.DAYS) {
            if (hours > 0 || (allZero && smallestUnit == ChronoUnit.HOURS)) {
                texts.add(context.getResources().getQuantityString(R.plurals.hour_short, hours, hours));
            }

            if (smallestUnit != ChronoUnit.HOURS) {
                if (minutes > 0 || (allZero && smallestUnit == ChronoUnit.MINUTES)) {
                    texts.add(context.getResources().getQuantityString(R.plurals.minute_short, minutes, minutes));
                }

                if (smallestUnit != ChronoUnit.MINUTES) {
                    if (seconds > 0 || (allZero && smallestUnit == ChronoUnit.SECONDS)) {
                        texts.add(context.getResources().getQuantityString(R.plurals.second_short, seconds, seconds));
                    }
                }
            }
        }

        return String.join(" ", texts);
    }

    /**
     * Given a reference time and a local time, create a timestamp in the future that is closest to the reference time.
     * This method is handy in situations where a local timestamp is provided without a date, and we need to determine the
     * actual intended date knowing only that it is near the reference time. Internally, there is a threshold of 5 minutes
     * where the returned time may be before the reference time, to account for the imprecision of the client/server time.
     *
     * @param nowZdt The reference time
     * @param time The local time
     * @return The timestamp either yesterday, today (preferably) or tomorrow, depending on the proximity to the reference time.
     */
    public static ZonedDateTime createTimeInNearFuture(ZonedDateTime nowZdt, LocalTime time) {
        int dayOffset = 0;
        long diffIfToday = ChronoUnit.MINUTES.between(nowZdt.toLocalDateTime(), nowZdt.toLocalDate().atTime(time));
        if (diffIfToday < -5) { //nejaka tolerance kvuli nepresnosti casu klient/server
            dayOffset = 1;
        }
        if (diffIfToday > 1440 - 5) {
            dayOffset = -1;
        }
        return nowZdt.toLocalDate().plusDays(dayOffset).atTime(time).atZone(nowZdt.getZone());
    }

    /**
     * Check if a given zoned date-time is the closest occurrence of its local time to another zoned date-time.
     * For example, if three zoned date-times of 10:00 at three consecutive days are given and the reference time is 10:30
     * of the middle day, the method will return true for the middle date-time and false for the other two.
     *
     * @param time The date-time to check
     * @param nowZdt The reference date-time
     * @return true/false
     */
    public static boolean isClosestSameLocalTo(ZonedDateTime time, ZonedDateTime nowZdt) {
        Duration diff = Duration.between(nowZdt.toLocalDateTime(), time.toLocalDateTime());
        if (diff.isNegative()) {
            return !diff.plusHours(12).isNegative();
        } else {
            return diff.minusHours(12).isNegative();
        }
    }

    private static final DecimalFormat MAX_1_DECIMAL = new DecimalFormat("#.#");

    /**
     * Format a time difference in minutes. See {@link #formatSmallTimeDifference(Context, int)}.
     *
     * @param context Context
     * @param duration The time difference, which will be converted to minutes
     * @return The formatted time difference
     */
    public static String formatSmallTimeDifference(Context context, Duration duration) {
        return formatSmallTimeDifference(context, (int) duration.toMinutes());
    }

    /**
     * Format a time difference in minutes. This method is mainly intended for formatting
     * intervals of less than 24 hours. Unlike {@link #formatTimeDifference(Context, Duration, ChronoUnit)},
     * this method will format half-hours as "X.5 hours" instead of "X hours and 30 minutes".
     * As a special case, though, 30 minutes will be formatted as 30 minutes instead of 0.5 hours.
     *
     * @param context Context
     * @param minutes The time difference in minutes
     * @return The formatted time difference
     */
    public static String formatSmallTimeDifference(Context context, int minutes) {
        //Specialni pripad - pulhodiny budeme formatovat jako X.5 hodiny, ne X hodin a 30 minut

        float hours = minutes / 60f;
        if (hours >= 1 && hours < 24 && hours % 1.0 == 0.5) {
            int relevantHours = 2; //"1.5 hodiny", "6.5 hodiny", nikoli "1.5 hodina" a "6.5 hodin"
            return context.getResources().getQuantityString(R.plurals.hour_short_fractional, relevantHours, MAX_1_DECIMAL.format(hours));
        }

        return formatTimeDifference(context, Duration.ofMinutes(minutes), ChronoUnit.MINUTES);
    }

    /**
     * Get the current age of a person given their birth date.
     *
     * @param birthDate The birth date of the person
     * @return The age of the person in whole years
     */
    public static int getAge(LocalDate birthDate) {
        return getAge(birthDate, LocalDate.now());
    }

    /**
     * Get the age of a person at a date.
     *
     * @param birthDate The birth date of the person
     * @param now The date at which to calculate the age
     * @return The age of the person in whole years
     */
    public static int getAge(LocalDate birthDate, LocalDate now) {
        return birthDate.until(now).getYears();
    }

    /**
     * Check if two date-times are equal when truncated to a given unit.
     *
     * @param a The first date-time
     * @param b The second date-time
     * @param truncationUnit The unit to which to truncate the date-times
     * @return True if the truncated date-times are equal, false otherwise
     */
    public static boolean equalsTruncated(@NonNull ZonedDateTime a, @NonNull ZonedDateTime b, @NonNull TemporalUnit truncationUnit) {
        return a.truncatedTo(truncationUnit).equals(b.truncatedTo(truncationUnit));
    }

    /**
     * Check if two date-times are equal when truncated to a given unit.
     *
     * @param a The first date-time
     * @param b The second date-time
     * @param truncationUnit The unit to which to truncate the date-times
     * @return True if the truncated date-times are equal, false otherwise
     */
    public static boolean equalsTruncated(@NonNull OffsetDateTime a, @NonNull OffsetDateTime b, @NonNull TemporalUnit truncationUnit) {
        return a.truncatedTo(truncationUnit).equals(b.truncatedTo(truncationUnit));
    }

    /**
     * Check if two date-times are equal when truncated to a given unit.
     *
     * @param a The first date-time
     * @param b The second date-time
     * @param truncationUnit The unit to which to truncate the date-times
     * @return True if the truncated date-times are equal, false otherwise
     */
    public static boolean equalsTruncated(@NonNull LocalDateTime a, @NonNull LocalDateTime b, @NonNull TemporalUnit truncationUnit) {
        return a.truncatedTo(truncationUnit).equals(b.truncatedTo(truncationUnit));
    }

    /**
     * Check if two instants are equal when truncated to a given unit.
     * <p>
     * Be aware that instants can only be truncated to units at most as coarse as seconds.
     *
     * @param a The first date-time
     * @param b The second date-time
     * @param truncationUnit The unit to which to truncate the date-times
     * @return True if the truncated date-times are equal, false otherwise
     */
    public static boolean equalsTruncated(@NonNull Instant a, @NonNull Instant b, @NonNull TemporalUnit truncationUnit) {
        return a.truncatedTo(truncationUnit).equals(b.truncatedTo(truncationUnit));
    }

    /**
     * Check if a date-time range fully contains another date-time range.
     *
     * @param containerStart Start date of the containing interval
     * @param containerEnd End date of the containing interval
     * @param start Start date of the contained interval
     * @param end End date of the contained interval
     * @return true/false
     */
    public static boolean intervalContains(@NonNull ZonedDateTime containerStart, @NonNull ZonedDateTime containerEnd, @NonNull ZonedDateTime start, @NonNull ZonedDateTime end) {
        return !start.isBefore(containerStart) && !end.isAfter(containerEnd);
    }

    /**
     * Check if a zoned timestamp's date corresponds to today's date in its own time zone.
     *
     * @param dt The timestamp
     * @return true/false
     */
    public static boolean isTodaySameZone(@NonNull ZonedDateTime dt) {
        return dt.toLocalDate().equals(ZonedDateTime.now(dt.getZone()).toLocalDate());
    }

    /**
     * Check if a offset timestamp's date corresponds to today's date in its own time offset.
     *
     * @param dt The timestamp
     * @return true/false
     */
    public static boolean isTodaySameOffset(@NonNull OffsetDateTime dt) {
        return dt.toLocalDate().equals(OffsetDateTime.now(dt.getOffset()).toLocalDate());
    }

    /**
     * Order of date and time in a formatted string.
     */
    public enum DateTimeFormatLayout {
        /**
         * First the date, then the time.
         */
        DATE_THEN_TIME,
        /**
         * First the time, then the date.
         */
        TIME_THEN_DATE
    }

    /**
     * Style/lenght of weekday representation.
     */
    public enum WeekdayStyle {
        /**
         * No weekday representation (omit the weekday name).
         */
        NONE,
        /**
         * Short/abbreviated form, e. g. "Mon".
         */
        SHORT,
        /**
         * Full form, e. g. "Monday".
         */
        FULL
    }

    /**
     * Declension of a date-time phrase.
     */
    public enum DateDeclension {
        /**
         * Nominative case, e. g. "pondělí 10. 3."
         */
        NOMINATIVE,
        /**
         * Accusative case, e. g. "ve středu 12. 3."
         */
        ACCUSATIVE
    }

    /**
     * Specialization of a relative date representation.
     */
    public enum RelativeDateFormatSpec {
        /**
         * Use the "today" string representation.
         */
        TODAY,
        /**
         * Use the "yesterday" string representation.
         */
        YESTERDAY,
        /**
         * Use the "tomorrow" string representation.
         */
        TOMORROW,
        /**
         * Use the week day representation, i. e. "Monday 10. 3."
         */
        WEEKDAY,
        /**
         * Use the full date representation, i. e. "10. 3. 2025"
         */
        DATE_FULL
    }

    /**
     * Builder-style parameters for formatting a relative date-time.
     * All operations on this class directly modify the instance and return it for chaining.
     */
    public static class RelativeFormatParams {

        private WeekdayStyle weekdayStyle = WeekdayStyle.FULL;
        private DateDeclension declension = DateDeclension.NOMINATIVE;
        private LocalDateTime asSeenFrom = LocalDateTime.now();
        private Integer useRelativeTimeOnlyLimit = null;
        private ChronoUnit relativeTimeSmallestUnit = ChronoUnit.MINUTES;
        private Predicate<LocalDateTime> useTimeOnlyCondition = duration -> false;

        /**
         * Set the style/length of date representation when {@link RelativeDateFormatSpec#WEEKDAY} is used.
         * Default is {@link WeekdayStyle#FULL}.
         *
         * @param weekdayStyle The weekday style
         * @return This instance for chaining
         */
        public RelativeFormatParams withWeekdayStyle(WeekdayStyle weekdayStyle) {
            this.weekdayStyle = weekdayStyle;
            return this;
        }

        /**
         * Set the declension of the date-time phrase.
         * Default is {@link DateDeclension#NOMINATIVE}.
         *
         * @param declension The declension
         * @return This instance for chaining
         */
        public RelativeFormatParams withDeclension(DateDeclension declension) {
            this.declension = declension;
            return this;
        }

        /**
         * Set the date-time relative to which date-times shall be formatted.
         * Default is the current date-time.
         *
         * @param asSeenFrom The date-time
         * @return This instance for chaining
         */
        public RelativeFormatParams withAsSeenFrom(LocalDateTime asSeenFrom) {
            this.asSeenFrom = asSeenFrom;
            return this;
        }

        /**
         * Limit until which the time-only relative format (e. g. "in 5 minutes") should be used.
         * By default, the time-only format is disabled.
         *
         * @param useRelativeTimeOnlyLimit The limit in minutes, or null to disable the time-only format.
         * @return This instance for chaining
         */
        public RelativeFormatParams withUseRelativeTimeOnlyLimit(Integer useRelativeTimeOnlyLimit) {
            this.useRelativeTimeOnlyLimit = useRelativeTimeOnlyLimit;
            return this;
        }

        /**
         * Set the smallest displayed unit when formatting relative time.
         * Default is {@link ChronoUnit#MINUTES} and the only other supported unit is {@link ChronoUnit#SECONDS}.
         *
         * @param relativeTimeSmallestUnit The smallest unit
         * @return This instance for chaining
         */
        public RelativeFormatParams withRelativeTimeSmallestUnit(ChronoUnit relativeTimeSmallestUnit) {
            this.relativeTimeSmallestUnit = relativeTimeSmallestUnit;
            return this;
        }

        /**
         * Set a condition for using the time-only format.
         * If the condition is met, the result will not include the date, only the time (possibly
         * relative if permitted).
         *
         * @param useTimeOnlyCondition The condition, which should return true if time-only format should be used for the specified date-time.
         * @return This instance for chaining
         */
        public RelativeFormatParams withUseTimeOnlyCondition(Predicate<LocalDateTime> useTimeOnlyCondition) {
            this.useTimeOnlyCondition = useTimeOnlyCondition;
            return this;
        }
    }
}
