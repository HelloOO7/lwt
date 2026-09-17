package cz.spojenka.lwt.cicomock.api.ticketing;

import java.util.List;

public record CICOEventBatch(List<CICOEventPush> events, long currentLocalTimestamp) {
}
