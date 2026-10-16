package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.apache.commons.lang3.builder.ToStringBuilder;
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
    @Column(columnDefinition = "text")
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

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("appId", appId)
                .append("activationTime", activationTime)
                .append("activationSource", activationSource)
                .append("activationSourceMetadata", activationSourceMetadata)
                .toString();
    }
}
