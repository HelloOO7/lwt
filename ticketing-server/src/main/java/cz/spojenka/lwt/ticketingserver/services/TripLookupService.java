package cz.spojenka.lwt.ticketingserver.services;

import com.carrotsearch.hppc.IntArrayList;
import cz.spojenka.datasources.enums.LineIDRegistry;
import cz.spojenka.datasources.model.timetable.ConnectionEntity;
import cz.spojenka.datasources.model.timetable.LineEntity;
import cz.spojenka.datasources.model.timetable.TimetableDatabase;
import cz.spojenka.engine.jni.SearchEnums;
import cz.spojenka.engine.jni.SearchLibrary;
import cz.spojenka.lwt.ticketingserver.model.LwtTicketMetadata;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class TripLookupService {

    private static final Logger logger = LoggerFactory.getLogger(TripLookupService.class);

    private final TimetableDatabase tt;
    private final SearchLibrary jni;

    private final Map<String, List<ConnectionEntity>> tripKeyIndex = new HashMap<>();

    public TripLookupService(TimetableDatabase tt, SearchLibrary jni) {
        this.tt = tt;
        this.jni = jni;
    }

    @PostConstruct
    private void buildTripKeyIndex() {
        logger.info("Building trip key index...");
        for (ConnectionEntity conn : tt.getConnections()) {
            for (var la : conn.getRoute().getLineAffiliations()) {
                LineEntity l = la.getLine();
                for (var lineNum : l.getRegistryIDsIterable()) {
                    String tn = conn.getConnNumber(lineNum.getKey());
                    if (tn != null) {
                        registerTripKey(lineNum.getValue(), tn, conn);
                    }
                    conn.getRunNumbers()
                            // do not care about the line registry associated with a run number, as they may be different (CIS vs ROPID/DPP)
                            .forEach(rn -> registerTripKey(lineNum.getValue(), processRunNumber(rn.getRegistryName(), rn.getNumberAsString()), conn));
                }
            }
        }
        logger.info("Index built successfully.");
    }

    private String processRunNumber(String reg, String runNumber) {
        if (LineIDRegistry.PID.equals(reg)) {
            return String.valueOf(pidRunNumToPackedInt(runNumber));
        }
        return runNumber;
    }

    private static int pidRunNumToPackedInt(String runNum) {
        String[] parts = runNum.split("/");
        if (parts.length == 2) {
            return Integer.parseInt(parts[0]) * 1000 + Integer.parseInt(parts[1]);
        }
        return 0;
    }

    private void registerTripKey(Object lineNum, String tripNum, ConnectionEntity conn) {
        String key = lineNum + "/" + tripNum;
        tripKeyIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(conn);
    }

    public ConnectionEntity lookupByLwtData(LwtTicketMetadata metadata, OffsetDateTime referenceTimepoint) {
        List<ConnectionEntity> connsByTk = tripKeyIndex.get(metadata.tripKey);
        if (connsByTk == null) {
            return null;
        }
        for (ConnectionEntity conn : connsByTk) {
            if (checkConnectionInService(conn, findStopIndexCandidates(conn, metadata), referenceTimepoint)) {
                return conn;
            }
        }
        return null;
    }

    private int[] findStopIndexCandidates(ConnectionEntity conn, LwtTicketMetadata metadata) {
        IntArrayList out = new IntArrayList();
        Integer stopCis = metadata.stopCis;
        for (var s = conn.stopTimes(); s.valid(); s.next()) {
            if (Objects.equals(s.getRouteStop().getStop().getCisID(), stopCis)) {
                if (checkArrDepMatch(s, metadata)) {
                    out.add(s.index());
                }
            }
        }
        return out.toArray();
    }

    private boolean checkArrDepMatch(ConnectionEntity.StopTimeAccessor st, LwtTicketMetadata md) {
        if (md.timetableDeparture != null) {
            if (Objects.equals(st.getEffectiveDepartureTime(), md.timetableDeparture)) {
                return true;
            }
        } else if (md.timetableArrival != null) {
            if (Objects.equals(st.getEffectiveArrivalTime(), md.timetableArrival)) {
                return true;
            }
        } else {
            return true;
        }
        return false;
    }

    private boolean checkConnectionInService(ConnectionEntity conn, int[] stopIndexes, OffsetDateTime referenceTimepoint) {
        var jniConn = jni.getConnection(conn.getID());
        for (int si : stopIndexes) {
            LocalDate dd = getClosestDispatchDayByLocalTime(conn, si, referenceTimepoint);
            if (jniConn.isInService(dd, -1, 0)) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate getClosestDispatchDayByLocalTime(ConnectionEntity conn, int stopIndex, OffsetDateTime candidateStopArr) {
        var st = conn.stopTimeAt(stopIndex);
        LocalTime stArr = st.getEffectiveArrivalTime();
        OffsetDateTime stArrAbs = candidateStopArr.with(stArr);
        var diff = Duration.between(stArrAbs, candidateStopArr);
        if (diff.abs().compareTo(Duration.ofHours(12)) > 0) {
            stArrAbs = stArrAbs.plusDays(diff.isPositive() ? -1 : 1);
        }
        return stArrAbs.toLocalDate().minusDays(st.getEffectiveArrivalOffset());
    }
}
