package cz.spojenka.lwt.ticketingserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import cz.spojenka.datasources.enums.ArrivalOrDeparture;
import cz.spojenka.datasources.enums.IDSID;
import cz.spojenka.datasources.enums.LineIDRegistry;
import cz.spojenka.datasources.model.timetable.ConnectionEntity;
import cz.spojenka.datasources.model.timetable.LineEntity;
import cz.spojenka.engine.jni.Trip;
import cz.spojenka.engine.jni.TripStop;
import cz.spojenka.lwt.ticketingserver.util.TimetableUtils;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Backup trip description encoded into CI/CO travel records
 * for use when the trip is no longer in the source database,
 * as an informative record to show to the user.
 */
@JsonPropertyOrder({"L", "LFN", "CN", "FSN", "TSN", "ST", "ET"})
public class TripDescBackup {

    @JsonProperty("L")
    public String lineNumber;
    @JsonProperty("LFN")
    public String lineFriendlyName;
    @JsonProperty("CN")
    public String connectionNumber;
    @JsonProperty("FSN")
    public String fromStopName;
    @JsonProperty("TSN")
    public String toStopName;
    @JsonProperty("ST")
    public ZonedDateTime startTime;
    @JsonProperty("ET")
    public ZonedDateTime endTime;

    public static TripDescBackup create(ConnectionEntity conn, TripStop refDepStop, int fromStop, int toStop, List<String> tariffPreference) {
        TripDescBackup res = new TripDescBackup();
        LineEntity line = conn.getRoute().getLineAtStop(fromStop);
        if (line == null) {
            line = conn.getRoute().getLineAtStop(toStop);
        }
        if (line != null) {
            for (String tp : tariffPreference) {
                try {
                    IDSID ids = IDSID.valueOf(tp);
                    var idsLink = line.getIDSAffiliation(ids);
                    if (idsLink != null && idsLink.getLocalLineCode() != null) {
                        res.lineFriendlyName = idsLink.getLocalLineCode();
                        break;
                    }
                } catch (IllegalArgumentException ignored) {

                }
            }
            if (res.lineFriendlyName == null) {
                res.lineFriendlyName = line.getRegistryID(LineIDRegistry.CIS);
            }
            if (res.lineFriendlyName == null) {
                res.lineFriendlyName = line.getRegistryID(LineIDRegistry.KADR_LINE_ABBREV);
            }
            res.lineNumber = line.getRegistryID(LineIDRegistry.CIS);
            if (res.lineNumber == null) {
                res.lineNumber = line.getRegistryID(LineIDRegistry.KADR_LINE_NUMBER);
            }
        }
        res.connectionNumber = conn.getConnNumber(LineIDRegistry.CIS, fromStop);
        if (res.connectionNumber == null) {
            res.connectionNumber = conn.getConnNumber(LineIDRegistry.KADR_OTN, fromStop);
        }
        res.fromStopName = TimetableUtils.getConnStopName(conn, fromStop);
        res.toStopName = TimetableUtils.getConnStopName(conn, toStop);
        res.startTime = TimetableUtils.getStopTimeByStopTime(conn, refDepStop, fromStop, ArrivalOrDeparture.DEPARTURE);
        res.endTime = TimetableUtils.getStopTimeByStopTime(conn, refDepStop, toStop, ArrivalOrDeparture.ARRIVAL);
        return res;
    }
}
