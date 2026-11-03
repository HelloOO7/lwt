package cz.spojenka.lwt.ticketingserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LwtCICOTicketMetadata extends LwtTicketMetadata {

    // only set for SELF checkouts, null otherwise.
    // on events of type CHECK_OUT, it is normally implied that they are LWT-issued.
    @JsonProperty("CO")
    public CheckOutType checkOutType;

    public static LwtCICOTicketMetadata parse(String data) {
        return parse(data, LwtCICOTicketMetadata.class);
    }

    public static enum CheckOutType {
        @JsonProperty("LWT")
        LWT,
        @JsonProperty("SELF")
        SELF
    }
}
