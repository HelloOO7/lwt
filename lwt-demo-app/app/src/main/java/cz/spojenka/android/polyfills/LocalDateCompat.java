package cz.spojenka.android.polyfills;

import android.os.Build;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class LocalDateCompat {

    public static LocalDate ofInstant(Instant instant, ZoneId zoneId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return LocalDate.ofInstant(instant, zoneId);
        } else {
            return ZonedDateTime.ofInstant(instant, zoneId).toLocalDate();
        }
    }
}
