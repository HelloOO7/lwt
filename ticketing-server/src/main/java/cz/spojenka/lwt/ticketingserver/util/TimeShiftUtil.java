package cz.spojenka.lwt.ticketingserver.util;

import cz.spojenka.lwt.ticketing.api.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.BiFunction;

public class TimeShiftUtil {

    private static <T extends IEventPush> T eventPushToGlobalTime(AbstractEventBatch<T> batch, T event, BiFunction<T, OffsetDateTime, T> changeTimeFunc, OffsetDateTime clientTime) {
        if (event.absoluteTimestamp() != null) {
            return event;
        }
        return changeTimeFunc.apply(event, clientTime.plus(Duration.ofMillis(event.localTimestamp() - batch.currentLocalTimestamp())));
    }

    private static <T extends IEventPush> List<T> eventPushListToGlobalTime(AbstractEventBatch<T> batch, BiFunction<T, OffsetDateTime, T> changeTimeFunc, OffsetDateTime clientTime) {
        return batch.events().stream().map(event -> eventPushToGlobalTime(batch, event, changeTimeFunc, clientTime)).toList();
    }

    public static List<CICOEventPush> toGlobalTime(CICOEventBatch batch) {
        return eventPushListToGlobalTime(batch, CICOEventPush::withAbsoluteTimestamp, OffsetDateTime.now());
    }

    public static List<TelemetryEventPush> toGlobalTime(TelemetryEventBatch batch) {
        return eventPushListToGlobalTime(batch, TelemetryEventPush::withAbsoluteTimestamp, OffsetDateTime.now());
    }

    public static void assertTimestampsAbsolute(List<? extends IEventPush> events) {
        for (IEventPush event : events) {
            if (event.absoluteTimestamp() == null) {
                throw new IllegalArgumentException("Event " + event + " does not have absolute timestamp");
            }
        }
    }
}
