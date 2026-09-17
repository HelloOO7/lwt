package cz.spojenka.lwt.cicomock.services;

import cz.spojenka.datasources.alg.HaversineFormula;
import cz.spojenka.datasources.enums.LineIDRegistry;
import cz.spojenka.datasources.enums.StopIDRegistry;
import cz.spojenka.datasources.enums.TransportMeans;
import cz.spojenka.datasources.model.timetable.*;
import cz.spojenka.engine.jni.VehicleLinearPosition;
import cz.spojenka.engine.jni.VehiclePositionResult;
import cz.spojenka.lwt.cicomock.api.lwtclient.LineType;
import cz.spojenka.lwt.cicomock.api.lwtclient.TripAdvertisementData;
import cz.spojenka.lwt.cicomock.api.lwtclient.TripAdvertisementDataExt;
import cz.spojenka.lwt.cicomock.model.Location;
import cz.spojenka.lwt.cicomock.model.NearbyDevice;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AdvDataConverter {

    private final TimetableDatabase tt;
    private final StopDataService stopDataService;

    public AdvDataConverter(TimetableDatabase tt, StopDataService stopDataService) {
        this.tt = tt;
        this.stopDataService = stopDataService;
    }

    public static byte[] makeFakeAddressForConnId(int connId) {
        return ByteBuffer.allocate(6)
                .putShort((short) 0xDE57)
                .putInt(connId)
                .array();
    }

    public static int computeDeviceRssi(NearbyDevice dev, Location location) {
        return computeRssiFromDistance(location, dev.location());
    }

    private static int computeRssiFromDistance(Location a, Location b) {
        double dist = HaversineFormula.haversineM(a.lat(), a.lon(), b.lat(), b.lon());

        return (int) calculateRssi(-70, dist, 2.5);
    }

    public TripAdvertisementDataExt makeTripInformation(VehiclePositionResult result) {
        ConnectionEntity conn = tt.getConnectionById(result.connectionId);
        RouteEntity route = conn.getRoute();
        int lineType = switch (route.getMeansOfTransport()) {
            case TRAIN -> LineType.GenericTrain;
            case BUS -> LineType.GenericBus;
            case TROLLEY -> LineType.Trolleybus;
            case METRO -> LineType.Metro;
            case FERRY -> LineType.Ferry;
            case TRAM -> LineType.Tram;
            case ROPEWAY -> LineType.Funicular;
            default -> LineType.GenericBus;
        };
        var routeStops = route.stops();
        StopPost directionStop = routeStops.at(route.lastStopIndex()).getStopPost();
        int directionCis = getStopCisNum(directionStop.getParent());
        int curStopIdx = result.linearPosition.lastStopIndex;
        StopPost curStop = routeStops.at(curStopIdx).getStopPost();
        int curDist = getStopDistMeters(routeStops);
        StopPost nextStop = null;
        int nextDist = curDist;
        if (curStopIdx < route.lastStopIndex()) {
            nextStop = routeStops.at(curStopIdx + 1).getStopPost();
            nextDist = getStopDistMeters(routeStops);
        }
        int locationState = deriveLocationState(result, curStop, curDist, nextStop, nextDist);
        int dataStopIdx = (locationState == TripAdvertisementData.LOCATION_STATE_AT_STOP || nextStop == null) ? curStopIdx : curStopIdx + 1;
        routeStops.at(dataStopIdx);
        var stopTimes = conn.stopTimes().at(dataStopIdx);
        int curCis = getStopCisNum(routeStops.getStop());
        LocalTime arr = stopTimes.getArrivalTime();
        LocalTime dep = stopTimes.getDepartureTime();
        int delay = 0;
        int flags = TripAdvertisementData.FLAG_CAN_USE_TICKETING | TripAdvertisementData.FLAG_CAN_USE_CICO;
        if (locationState == TripAdvertisementData.LOCATION_STATE_AT_STOP) {
            flags |= TripAdvertisementData.FLAG_IS_AT_STOP;
        }
        String curStopName = getStopName(routeStops.getStopPost(), false);
        String headsign = getStopName(directionStop, true);

        LineEntity line = route.getLineAtStop(dataStopIdx);
        boolean isTrain = route.getMeansOfTransport() == TransportMeans.TRAIN;
        int lineLicenseNumber = getLineLicenseNumber(line, isTrain);
        int tripNumber = getTripNumber(conn);

        String lineName = line != null ? getLineIDSName(line, String.valueOf(isTrain ? tripNumber : lineLicenseNumber)) : "?";

        return new TripAdvertisementDataExt(
                new TripAdvertisementData(
                        lineType,
                        lineLicenseNumber,
                        tripNumber,
                        directionCis,
                        curCis,
                        locationState,
                        arr,
                        dep,
                        delay,
                        flags
                ),
                curStopName,
                lineName,
                headsign
        );
    }

    public static String getLineName(ConnectionEntity conn, VehicleLinearPosition pos) {
        RouteEntity route = conn.getRoute();
        var routeStops = route.stops();
        int curStopIdx = pos.lastStopIndex;
        int curDist = getStopDistMeters(routeStops);
        int dataStopIdx = (pos.distanceTraversed == curDist || curStopIdx == route.lastStopIndex()) ? curStopIdx : curStopIdx + 1;

        LineEntity line = route.getLineAtStop(dataStopIdx);
        boolean isTrain = route.getMeansOfTransport() == TransportMeans.TRAIN;

        int lineLicenseNumber = getLineLicenseNumber(line, isTrain);
        int tripNumber = getTripNumber(conn);

        return line != null ? getLineIDSName(line, String.valueOf(isTrain ? tripNumber : lineLicenseNumber)) : "?";
    }

    private static int getStopDistMeters(RouteEntity.RouteStopAccessor stop) {
        return (int) (stop.getKilometers() * 1000);
    }

    private static int getTripNumber(ConnectionEntity conn) {
        int tripNumber = 0;
        Integer tripCisNum = conn.getConnNumberInt(LineIDRegistry.CIS);
        if (tripCisNum != null) {
            tripNumber = tripCisNum;
        } else {
            Integer tripOtn = conn.getConnNumberInt(LineIDRegistry.KADR_OTN);
            if (tripOtn != null) {
                tripNumber = tripOtn;
            }
        }
        return tripNumber;
    }

    private static int getLineLicenseNumber(LineEntity line, boolean isTrain) {
        int lineLicenseNumber = 0;
        if (line != null) {
            Integer cisNum = line.getRegistryIDInt(LineIDRegistry.CIS);
            if (cisNum != null) {
                lineLicenseNumber = cisNum;
            }
            if (lineLicenseNumber == 0 && isTrain) {
                String lineNum = line.getRegistryID(LineIDRegistry.KADR_LINE_ABBREV);
                if (lineNum != null) {
                    lineLicenseNumber = makeTrainLineNumber(lineNum);
                }
            }
        }
        return lineLicenseNumber;
    }

    private int deriveLocationState(VehiclePositionResult result, StopPost curStop, int curStopDist, StopPost nextStop, int nextStopDist) {
        if (result.linearPosition.distanceTraversed == curStopDist) {
            return TripAdvertisementData.LOCATION_STATE_AT_STOP;
        }
        if (isInStopRadius(curStop, result)) {
            return TripAdvertisementData.LOCATION_STATE_AFTER_STOP;
        }
        if (isInStopRadius(nextStop, result)) {
            return TripAdvertisementData.LOCATION_STATE_BEFORE_STOP;
        }
        return TripAdvertisementData.LOCATION_STATE_BETWEEN_STOPS;
    }

    private float getStopRadius(StopPost stop) {
        var stopInfo = stopDataService.getStopInfo(getAswId(stop));
        if (stopInfo == null) {
            return 60; //default
        }
        return stopInfo.stopRadius();
    }

    private static String getLineIDSName(LineEntity line, String defaultVal) {
        for (LineEntity.IDSAffiliation ids : line.getIDS()) {
            if (ids.getLocalLineCode() != null) {
                return ids.getLocalLineCode();
            }
        }
        return defaultVal;
    }

    private String getStopName(StopPost stop, boolean forHeadsign) {
        var stopInfo = stopDataService.getStopInfo(getAswId(stop));
        if (stopInfo == null) {
            if (stop.getOverridingName() != null) {
                return stop.getOverridingName();
            } else {
                return stop.getParent().getName();
            }
        }
        return PictogramStripper.stripPictograms(forHeadsign ? stopInfo.headsignName() : stopInfo.lcdName());
    }

    private String getAswId(StopPost stop) {
        return stop.getRegistryID(StopIDRegistry.PID_ASW);
    }

    private boolean isInStopRadius(StopPost stop, VehiclePositionResult result) {
        if (!stop.hasGPS()) {
            return false;
        }
        double dist = HaversineFormula.haversineM(stop.getGpsLat(), stop.getGpsLon(), result.lat, result.lon);
        return dist <= getStopRadius(stop);
    }

    private static final Pattern TRAIN_LINE_NUM_REGEX = Pattern.compile("([A-Za-z]*)([0-9]+)");

    private static int makeTrainLineNumber(String lineNum) {
        var matcher = TRAIN_LINE_NUM_REGEX.matcher(lineNum);
        if (matcher.matches()) {
            return TripAdvertisementData.makeTrainLineNumber(matcher.group(1), Integer.parseInt(matcher.group(2)));
        }
        return 0;
    }

    private int getStopCisNum(StopEntity stop) {
        Integer val = stop.getCisID();
        if (val != null) {
            return val;
        }
        return 0;
    }

    private static double calculateRssi(
            double rssiAt1m,
            double distanceMeters,
            double pathLossExponent) {

        if (distanceMeters < 0) {
            throw new IllegalArgumentException("Distance must be > 0");
        } else if (distanceMeters <= 1.0) {
            return rssiAt1m;
        }

        return rssiAt1m - 10.0 * pathLossExponent * Math.log10(distanceMeters);
    }

    public static double estimateRadiusMetersForRssi(int thresholdRssi) {
        return estimateRadiusMetersForRssi(thresholdRssi, -70, 2.5);
    }

    public static double estimateRadiusMetersForRssi(int thresholdRssi, double rssiAt1m, double pathLossExponent) {
        if (thresholdRssi >= rssiAt1m) {
            return 1.0;
        }
        double meters = Math.pow(10.0, (rssiAt1m - thresholdRssi) / (10.0 * pathLossExponent));
        return Math.max(1.0, meters);
    }

    public static String createLwtMetadataFromAdvData(TripAdvertisementData advData) {
        List<String> parts = new ArrayList<>();
        if (advData.getLineLicenseNumber() != 0) {
            String tk = Integer.toString(advData.getLineLicenseNumber());
            if (advData.getTripNumber() != 0) {
                tk += "/" + advData.getTripNumber();
            }
            parts.add("TK:" + tk);
        }
        if (advData.getStopDepTime() != null) {
            parts.add("TTD:" + advData.getStopDepTime().toString());
        } else if (advData.getStopArrTime() != null) {
            parts.add("TTA:" + advData.getStopDepTime().toString());
        }
        if (advData.getStopCisNumber() != 0) {
            parts.add("S:" + advData.getStopCisNumber());
        }
        parts.add("LS:" + convertLocationStateFromAdv(advData.getLocationState()));

        return String.join("|", parts);
    }

    private static String convertLocationStateFromAdv(int advLocationState) {
        return switch (advLocationState) {
            case TripAdvertisementData.LOCATION_STATE_AT_STOP -> ".";
            case TripAdvertisementData.LOCATION_STATE_BETWEEN_STOPS -> "=";
            case TripAdvertisementData.LOCATION_STATE_BEFORE_STOP -> ">";
            case TripAdvertisementData.LOCATION_STATE_AFTER_STOP -> "<";
            default -> throw new IllegalArgumentException("Unknown location state: " + advLocationState);
        };
    }
}
