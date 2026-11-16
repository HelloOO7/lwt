package cz.spojenka.lwt.ticketing.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TelemetryEventBatch extends AbstractEventBatch<TelemetryEventPush> {

    @JsonCreator
    public TelemetryEventBatch(@JsonProperty("events") List<TelemetryEventPush> events, @JsonProperty("currentLocalTimestamp") long currentLocalTimestamp) {
        super(events, currentLocalTimestamp);
    }
}
