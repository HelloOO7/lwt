package cz.spojenka.lwt.util;

import java.time.LocalDateTime;

public class LwtTime {

    public static LocalDateTime parseLocalTimestamp(long timestamp) {
        if (timestamp < 0) {
            return null;
        }
        return LocalDateTime.ofEpochSecond(timestamp, 0, java.time.ZoneOffset.UTC);
    }
}
