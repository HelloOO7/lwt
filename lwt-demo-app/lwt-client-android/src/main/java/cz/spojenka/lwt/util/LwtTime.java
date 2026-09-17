package cz.spojenka.lwt.util;

import com.google.flatbuffers.FlatBufferBuilder;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import cz.spojenka.lwt.LwtLocalDateTime;
import cz.spojenka.lwt.LwtOffsetDateTime;

public class LwtTime {

    public static LocalDateTime convertLocalDateTime(LwtLocalDateTime localDateTime) {
        if (localDateTime == null || localDateTime.localInstant() <= 0) {
            return null;
        }
        return LocalDateTime.ofEpochSecond(localDateTime.localInstant(), 0, ZoneOffset.UTC);
    }

    public static OffsetDateTime convertOffsetDateTime(LwtOffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return null;
        }
        return OffsetDateTime.of(convertLocalDateTime(offsetDateTime.localTime()), ZoneOffset.ofTotalSeconds(offsetDateTime.zoneOffset()));
    }

    public static int createLocalDateTime(FlatBufferBuilder builder, LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return 0;
        }
        return LwtLocalDateTime.createLwtLocalDateTime(builder, localDateTime.toEpochSecond(ZoneOffset.UTC));
    }

    public static int createOffsetDateTime(FlatBufferBuilder builder, OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return 0;
        }
        OffsetDateTime localInstant = OffsetDateTime.of(offsetDateTime.toLocalDateTime(), ZoneOffset.UTC);
        return LwtOffsetDateTime.createLwtOffsetDateTime(builder, localInstant.toEpochSecond(), offsetDateTime.getOffset().getTotalSeconds());
    }
}
