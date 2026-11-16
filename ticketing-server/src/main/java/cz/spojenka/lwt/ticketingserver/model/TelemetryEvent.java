package cz.spojenka.lwt.ticketingserver.model;

import cz.spojenka.lwt.ticketing.api.CICOEventType;
import cz.spojenka.lwt.ticketing.api.TelemetryEventClass;
import jakarta.persistence.*;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

import java.time.OffsetDateTime;

@Entity
@Table(
        indexes = {
                @Index(name = "idx_sender_id_event_time", columnList = "senderId,eventTime"),
                @Index(name = "idx_event_class_event_type_event_time", columnList = "eventClass,eventType,eventTime"),
                @Index(name = "idx_event_class_event_type_event_parameter", columnList = "eventClass,eventType,eventParameter")
        }
)
public class TelemetryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64)
    private String senderId;
    @TimeZoneStorage(TimeZoneStorageType.AUTO)
    private OffsetDateTime eventTime;

    private TelemetryEventClass eventClass;
    @Column(length = 32)
    private String eventType;
    private String eventParameter;

    @Column(columnDefinition = "text")
    private String extraData;

    TelemetryEvent() {

    }

    public TelemetryEvent(String senderId, OffsetDateTime eventTime, TelemetryEventClass eventClass, String eventType, String eventParameter, String extraData) {
        this.senderId = senderId;
        this.eventTime = eventTime;
        this.eventClass = eventClass;
        this.eventType = eventType;
        this.eventParameter = eventParameter;
        this.extraData = extraData;
    }

    public Long getId() {
        return id;
    }

    public String getSenderId() {
        return senderId;
    }

    public OffsetDateTime getEventTime() {
        return eventTime;
    }

    public TelemetryEventClass getEventClass() {
        return eventClass;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventParameter() {
        return eventParameter;
    }

    public String getExtraData() {
        return extraData;
    }
}
