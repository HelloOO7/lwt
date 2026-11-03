package cz.spojenka.lwt.ticketingserver.util;

import cz.spojenka.datasources.enums.ArrivalOrDeparture;
import cz.spojenka.datasources.model.timetable.ConnectionEntity;
import cz.spojenka.engine.jni.TripStop;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TimetableUtils {

    public static ZonedDateTime getStopTimeByStopTime(ConnectionEntity conn, TripStop refDepStop, int dstStopIdx, ArrivalOrDeparture type) {
        var fromStop = conn.stopTimeAt(refDepStop.routeIndex);
        var toStop = conn.stopTimeAt(dstStopIdx);
        ZonedDateTime time = refDepStop.time;
        int toOff = getStopTimeOffset(toStop, type);
        LocalTime toTime = getStopTimeLocalTime(toStop, type);
        time = time.plusDays(toOff - fromStop.getDepartureOffset());
        time = time.toLocalDate().atTime(toTime).atZone(time.getZone());
        return time;
    }

    private static int getStopTimeOffset(ConnectionEntity.StopTimeAccessor stopTimes, ArrivalOrDeparture type) {
        return type == ArrivalOrDeparture.ARRIVAL ? stopTimes.getArrivalOffset() : stopTimes.getDepartureOffset();
    }

    private static LocalTime getStopTimeLocalTime(ConnectionEntity.StopTimeAccessor stopTimes, ArrivalOrDeparture type) {
        return type == ArrivalOrDeparture.ARRIVAL ? stopTimes.getArrivalTime() : stopTimes.getDepartureTime();
    }

    public static ZonedDateTime getStopTimeByDispatch(ConnectionEntity conn, LocalDate dispatchDay, int stopIdx, ArrivalOrDeparture type, ZoneId timeZone) {
        ConnectionEntity.StopTimeAccessor stopTime = conn.stopTimeAt(stopIdx);
        return dispatchDay.plusDays(getStopTimeOffset(stopTime, type)).atTime(getStopTimeLocalTime(stopTime, type)).atZone(timeZone);
    }

    public static String getConnStopName(ConnectionEntity conn, int index) {
        return conn.getRoute().stops().at(index).getStop().getName();
    }
}
