package cz.spojenka.android.polyfills;

import android.os.Build;

import java.time.Duration;

public class DurationCompat {

    public static long toDaysPart(Duration duration) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return duration.toDaysPart();
        }
        else {
            return duration.getSeconds() / 86400;
        }
    }

    public static int toHoursPart(Duration duration) {
        return (int) (duration.toHours() % 24);
    }

    public static int toMinutesPart(Duration duration) {
        return (int) (duration.toMinutes() % 60);
    }

    public static int toSecondsPart(Duration duration) {
        return (int) (duration.getSeconds() % 60);
    }
}
