package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.Embeddable;
import org.jspecify.annotations.NonNull;

import java.time.OffsetDateTime;

@Embeddable
public class TicketActivationRecord {

    @NonNull
    private String appId;
    @NonNull
    private OffsetDateTime activationTime;

    @NonNull
    private TicketActivationSource activationSource;
    @NonNull
    private String activationSourceMetadata;

    public TicketActivationRecord(
            @NonNull String appId,
            @NonNull OffsetDateTime activationTime,
            @NonNull TicketActivationSource activationSource,
            @NonNull String activationSourceMetadata
    ) {
        this.appId = appId;
        this.activationTime = activationTime;
        this.activationSource = activationSource;
        this.activationSourceMetadata = activationSourceMetadata;
    }

    TicketActivationRecord() {
        this("", OffsetDateTime.now(), TicketActivationSource.USER, "");
    }

    public @NonNull String getAppId() {
        return appId;
    }

    public @NonNull OffsetDateTime getActivationTime() {
        return activationTime;
    }

    public @NonNull TicketActivationSource getActivationSource() {
        return activationSource;
    }

    public @NonNull String getActivationSourceMetadata() {
        return activationSourceMetadata;
    }
}
