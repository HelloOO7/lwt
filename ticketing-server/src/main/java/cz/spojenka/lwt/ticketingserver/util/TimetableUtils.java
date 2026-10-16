package cz.spojenka.lwt.ticketingserver.util;

import cz.spojenka.datasources.enums.ArrivalOrDeparture;
import cz.spojenka.datasources.model.timetable.ConnectionEntity;
import cz.spojenka.engine.jni.TripStop;

import java.time.LocalTime;
import java.time.ZonedDateTime;

public class TimetableUtils {

    public static ZonedDateTime getStopTimeByStopTime(ConnectionEntity conn, TripStop refDepStop, int dstStopIdx, ArrivalOrDeparture type) {
        var fromStop = conn.stopTimeAt(refDepStop.routeIndex);
        var toStop = conn.stopTimeAt(dstStopIdx);
        ZonedDateTime time = refDepStop.time;
        int toOff = type == ArrivalOrDeparture.ARRIVAL ? toStop.getArrivalOffset() : toStop.getDepartureOffset();
        LocalTime toTime = type == ArrivalOrDeparture.ARRIVAL ? toStop.getArrivalTime() : toStop.getDepartureTime();
        time = time.plusDays(toOff - fromStop.getDepartureOffset());
        time = time.toLocalDate().atTime(toTime).atZone(time.getZone());
        return time;
    }

    public static String getConnStopName(ConnectionEntity conn, int index) {
        return conn.getRoute().stops().at(index).getStop().getName();
    }
}
