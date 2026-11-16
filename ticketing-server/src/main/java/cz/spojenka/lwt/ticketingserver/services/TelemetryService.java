package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketing.api.TelemetryEventBatch;
import cz.spojenka.lwt.ticketing.api.TelemetryEventClass;
import cz.spojenka.lwt.ticketing.api.TelemetryEventPush;
import cz.spojenka.lwt.ticketingserver.model.TelemetryEvent;
import cz.spojenka.lwt.ticketingserver.model.TelemetryRepository;
import cz.spojenka.lwt.ticketingserver.util.TimeShiftUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class TelemetryService {

    private final TelemetryRepository repository;
    private final ObjectMapper jackson;

    public TelemetryService(TelemetryRepository repository, ObjectMapper jackson) {
        this.repository = repository;
        this.jackson = jackson;
    }

    @Transactional
    public void ingestEvents(TelemetryEventBatch events) {
        ingestEvents(TimeShiftUtil.toGlobalTime(events));
    }

    @Transactional
    public void ingestEvents(List<TelemetryEventPush> events) {
        if (events.isEmpty()) {
            return;
        }

        TimeShiftUtil.assertTimestampsAbsolute(events);

        insertAllEvents(
                events
                        .stream()
                        .sorted(Comparator.comparing(TelemetryEventPush::absoluteTimestamp))
                        .map(this::eventFromPush)
                        .toList()
        );
    }

    private void insertAllEvents(List<TelemetryEvent> events) {
        repository.saveAll(events);
    }

    private TelemetryEvent eventFromPush(TelemetryEventPush push) {
        return new TelemetryEvent(
                push.senderId(),
                push.absoluteTimestamp(),
                push.eventClass(),
                push.eventType(),
                push.eventParameter(),
                stringifyExtraData(push.extraData())
        );
    }

    private String stringifyExtraData(Object extraData) {
        if (extraData == null) {
            return null;
        }
        if (extraData instanceof String str) {
            return str;
        }
        return jackson.writeValueAsString(extraData);
    }

    public void ingestLocalEvent(String eventType, Object eventParameter, Object extraData) {
        ingestLocalEvent(eventType, eventParameter, stringifyExtraData(extraData));
    }

    public void ingestLocalEvent(String eventType, Object eventParameter, String extraData) {
        TelemetryEvent event = new TelemetryEvent(
                "root",
                OffsetDateTime.now(),
                TelemetryEventClass.MOS,
                eventType,
                eventParameter != null ? eventParameter.toString() : null,
                extraData
        );
        repository.save(event);
    }
}
