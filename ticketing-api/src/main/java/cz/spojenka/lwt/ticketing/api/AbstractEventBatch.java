package cz.spojenka.lwt.ticketing.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

public abstract class AbstractEventBatch<T extends IEventPush> implements Serializable {

    private final List<T> events;
    private final long currentLocalTimestamp;

    public AbstractEventBatch(List<T> events, long currentLocalTimestamp) {
        this.events = events;
        this.currentLocalTimestamp = currentLocalTimestamp;
    }

    @JsonProperty
    public List<T> events() {
        return events;
    }

    @JsonProperty
    public long currentLocalTimestamp() {
        return currentLocalTimestamp;
    }
}
