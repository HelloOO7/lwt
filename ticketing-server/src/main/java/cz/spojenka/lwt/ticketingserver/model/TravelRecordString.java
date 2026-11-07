package cz.spojenka.lwt.ticketingserver.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import cz.spojenka.datasources.persistentids.ParameterLine;

import java.util.List;

@JsonPropertyOrder({"CAN", "BU"})
public class TravelRecordString {

    private static final ParameterLine CODEC = ParameterLine.withSeparator('|');

    @JsonProperty("CAN")
    public String canonical;
    @JsonProperty("BU")
    public String backup;

    @JsonCreator
    public TravelRecordString(
            @JsonProperty("CAN") String canonical,
            @JsonProperty("BU") String backup
    ) {
        this.canonical = canonical;
        this.backup = backup;
    }

    public String encode() {
        return CODEC.encodePOJO(this);
    }

    public static TravelRecordString parse(String encoded) {
        return CODEC.decodePOJO(encoded, TravelRecordString.class);
    }

    public static String joinMultiple(List<TravelRecordString> travelRecords) {
        return ParameterLine.escapeAndJoin(travelRecords.stream().map(TravelRecordString::encode).toList(), '>');
    }

    public static List<TravelRecordString> splitMultiple(String encoded) {
        return ParameterLine.splitAndUnescape(encoded, '>').stream().map(TravelRecordString::parse).toList();
    }
}
