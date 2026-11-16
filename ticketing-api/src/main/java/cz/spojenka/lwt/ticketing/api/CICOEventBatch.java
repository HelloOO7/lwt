package cz.spojenka.lwt.ticketing.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CICOEventBatch extends AbstractEventBatch<CICOEventPush> {

    @JsonCreator
    public CICOEventBatch(@JsonProperty("events") List<CICOEventPush> events, @JsonProperty("currentLocalTimestamp") long currentLocalTimestamp) {
        super(events, currentLocalTimestamp);
    }
}
