package cz.spojenka.lwt.ticketingserver.model;

import cz.spojenka.lwt.ticketing.api.CICOEventType;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        indexes = {
                @Index(name = "idx_session_id_event_time", columnList = "sessionId,eventTime")
        }
)
public class CICOEvent {

    @Id
    private UUID id;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    private CICOEvent predecessor;

    private UUID sessionId;
    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;

    @TimeZoneStorage(TimeZoneStorageType.AUTO)
    private OffsetDateTime eventTime;
    private CICOEventType eventType;
    private String lwtMetadata;

    CICOEvent() {

    }

    public CICOEvent(UUID id, @Nullable CICOEvent predecessor, UUID sessionId, Account account, OffsetDateTime eventTime, CICOEventType eventType, String lwtMetadata) {
        this.id = id;
        this.predecessor = predecessor;
        this.sessionId = sessionId;
        this.account = account;
        this.eventTime = eventTime;
        this.eventType = eventType;
        this.lwtMetadata = lwtMetadata;
    }

    public UUID getId() {
        return id;
    }

    @Nullable
    public CICOEvent getPredecessor() {
        return predecessor;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Account getAccount() {
        return account;
    }

    public OffsetDateTime getEventTime() {
        return eventTime;
    }

    public CICOEventType getEventType() {
        return eventType;
    }

    public String getLwtMetadata() {
        return lwtMetadata;
    }
}
