package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketingserver.api.CICOEventPush;
import cz.spojenka.lwt.ticketingserver.model.CICOEvent;
import cz.spojenka.lwt.ticketingserver.model.CICOEventType;
import cz.spojenka.lwt.ticketingserver.model.CICORepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CICOIngestionService {

    private final CICORepository repository;

    public CICOIngestionService(CICORepository repository) {
        this.repository = repository;
    }

    public void ingestEvents(List<CICOEventPush> events) {
        if (events.isEmpty()) {
            return;
        }

        List<CICOEventPush> sortedEvents = new ArrayList<>(events);
        for (CICOEventPush event : sortedEvents) {
            if (event.absoluteTimestamp() == null) {
                throw new IllegalArgumentException("Event must have absolute timestamp");
            }
        }
        sortedEvents.sort(Comparator.comparing(CICOEventPush::absoluteTimestamp));

        List<CICOEvent> newEvents = new ArrayList<>(sortedEvents.size());
        CICOEvent lastEvent = null;
        for (CICOEventPush event : sortedEvents) {
            lastEvent = newEventFromPush(event, lastEvent);
            newEvents.add(lastEvent);
        }

        insertAllEvents(newEvents);
    }

    @Transactional
    private void insertAllEvents(List<CICOEvent> events) {
        repository.saveAll(events);
    }

    private CICOEvent newEventFromPush(CICOEventPush push, CICOEvent localChronologicalAncestor) {
        CICOEvent previousEvent;
        if (isNilUUID(push.previousEventId())) {
            if (push.eventType() == CICOEventType.CHECK_IN) {
                previousEvent = null;
            } else {
                previousEvent = repository.findNewestEventInSession(push.sessionId());
                if (previousEvent == null || (localChronologicalAncestor != null && localChronologicalAncestor.getEventTime().isAfter(previousEvent.getEventTime()))) {
                    previousEvent = localChronologicalAncestor;
                }
                if (previousEvent == null) {
                    throw new IllegalArgumentException("Event of type " + push.eventType() + " can not be the first event in session " + push.sessionId());
                }
            }
        } else {
            previousEvent = repository.findById(push.previousEventId()).orElseThrow(() -> new IllegalArgumentException("Previous event with id " + push.previousEventId() + " not found."));
        }
        long realAccountId = push.accountId();
        if (previousEvent != null) {
            if (!push.sessionId().equals(previousEvent.getSessionId())) {
                throw new IllegalArgumentException("All events in one session must have the same sessionId");
            }
            if (realAccountId != 0) {
                if (realAccountId != previousEvent.getAccountId()) {
                    throw new IllegalArgumentException("All events in one session must be connected to the same account");
                }
            } else {
                realAccountId = previousEvent.getAccountId();
            }
        } else if (realAccountId == 0) {
            throw new IllegalArgumentException("First event in a session must have a non-zero accountId");
        }
        return new CICOEvent(push.eventId(), previousEvent, push.sessionId(), realAccountId, push.absoluteTimestamp(), push.eventType(), push.lwtMetadata());
    }

    private boolean isNilUUID(UUID uuid) {
        return uuid == null || (uuid.getLeastSignificantBits() == 0 && uuid.getMostSignificantBits() == 0);
    }
}
