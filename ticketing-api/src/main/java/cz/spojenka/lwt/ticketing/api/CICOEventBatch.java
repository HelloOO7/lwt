package cz.spojenka.lwt.ticketing.api;

import java.util.List;

public record CICOEventBatch(List<CICOEventPush> events, long currentLocalTimestamp) {
}
