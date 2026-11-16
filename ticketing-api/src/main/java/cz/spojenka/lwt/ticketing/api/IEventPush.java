package cz.spojenka.lwt.ticketing.api;

import java.time.OffsetDateTime;

public interface IEventPush {

    public OffsetDateTime absoluteTimestamp();
    public long localTimestamp();
}
