package cz.spojenka.lwt.cicomock.api.lwtclient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalTime;

public class TripAdvertisementData {

    public static final int BYTES = 20;

    public static final int FLAG_IS_AT_STOP = 1;
    public static final int FLAG_CAN_USE_TICKETING = 2;
    public static final int FLAG_CAN_USE_CICO = 4;

    public static final int LOCATION_STATE_AT_STOP = 0;
    public static final int LOCATION_STATE_BETWEEN_STOPS = 1;
    public static final int LOCATION_STATE_BEFORE_STOP = 2;
    public static final int LOCATION_STATE_AFTER_STOP = 3;

    private final int lineType;
    private final int lineLicenseNumber;
    private final int tripNumber;
    private final int directionCisNumber;

    private final int stopCisNumber;
    private final int locationState;
    private final LocalTime stopArrTime;
    private final LocalTime stopDepTime;
    private final int delay;
    private final int flags;

    public TripAdvertisementData(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        lineType = dis.readUnsignedByte(); // +0x0
        lineLicenseNumber = readInt24(dis); // +0x1
        tripNumber = readInt24(dis); // +0x4
        directionCisNumber = dis.readInt(); // +0x7
        int stopUnion = dis.readInt(); // +0xB
        stopCisNumber = stopUnion & 0xFFFFFFF;
        locationState = (stopUnion >> 28) & 0xF;
        int timeUnion = dis.readInt(); // +0xF
        stopArrTime = convertTime(timeUnion);
        stopDepTime = convertTime(timeUnion >> 11);
        delay = timeUnion >> 22;
        flags = dis.readUnsignedByte(); // +0x13
        // total 0x14 = 20 bytes
    }

    public TripAdvertisementData(
            int lineType,
            int lineLicenseNumber,
            int tripNumber,
            int directionCisNumber,
            int stopCisNumber,
            int locationState,
            LocalTime stopArrTime,
            LocalTime stopDepTime,
            int delay,
            int flags
    ) {
        this.lineType = lineType;
        this.lineLicenseNumber = lineLicenseNumber;
        this.tripNumber = tripNumber;
        this.directionCisNumber = directionCisNumber;
        this.stopCisNumber = stopCisNumber;
        this.locationState = locationState;
        this.stopArrTime = stopArrTime;
        this.stopDepTime = stopDepTime;
        this.delay = delay;
        this.flags = flags;
    }

    public TripAdvertisementData(TripAdvertisementData copy) {
        this(copy.lineType, copy.lineLicenseNumber, copy.tripNumber, copy.directionCisNumber, copy.stopCisNumber, copy.locationState, copy.stopArrTime, copy.stopDepTime, copy.delay, copy.flags);
    }

    public void write(OutputStream out) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeByte(lineType);
        writeInt24(dos, lineLicenseNumber);
        writeInt24(dos, tripNumber);
        dos.writeInt(directionCisNumber);
        dos.writeInt(stopCisNumber | (locationState << 28));
        int timeUnion = (delay << 22) | (convertTime(stopDepTime) << 11) | convertTime(stopArrTime);
        dos.writeInt(timeUnion);
        dos.writeByte(flags);
    }

    public int getLineType() {
        return lineType;
    }

    public boolean isTrain() {
        switch (getLineType()) {
            case LineType.GenericTrain:
            case LineType.ExTrain:
            case LineType.OsTrain:
            case LineType.RTrain:
            case LineType.SpTrain:
                return true;
            default:
                return false;
        }
    }

    public int getLineLicenseNumber() {
        return lineLicenseNumber;
    }

    @JsonIgnore
    public String getParsedTrainLineNumber() {
        if (isTrain()) {
            int lic = getLineLicenseNumber();
            StringBuilder sb = new StringBuilder();
            // first two 7-bit ASCII bytes as string
            char ch1 = (char) ((lic >> 17) & 0x7F);
            char ch2 = (char) ((lic >> 10) & 0x7F);
            if (ch1 != 0) {
                sb.append(ch1);
            }
            if (ch2 != 0) {
                sb.append(ch2);
            }
            // remainder as a number
            sb.append(lic & 0x3FF);
            return sb.toString();
        } else {
            throw new IllegalStateException("Line is not a train");
        }
    }

    public static int makeTrainLineNumber(String trainTypeCode, int lineNumber) {
        int out = 0;
        for (int i = 0; i < Math.min(trainTypeCode.length(), 2); i++) {
            char ch = trainTypeCode.charAt(i);
            if (ch > 0x7F) {
                throw new IllegalArgumentException("Train type code must be ASCII");
            }
            out = (out << 7) | ch;
        }
        return (out << 10) | (lineNumber & 0x3FF);
    }

    public int getTripNumber() {
        return tripNumber;
    }

    public int getDirectionCisNumber() {
        return directionCisNumber;
    }

    public int getStopCisNumber() {
        return stopCisNumber;
    }

    public int getLocationState() {
        return locationState;
    }

    public LocalTime getStopArrTime() {
        return stopArrTime;
    }

    public LocalTime getStopDepTime() {
        return stopDepTime;
    }

    public int getDelay() {
        return delay;
    }

    public boolean isAtStop() {
        return (flags & FLAG_IS_AT_STOP) != 0;
    }

    public boolean isEnRoute() {
        return lineLicenseNumber != 0 && directionCisNumber != 0;
    }

    public boolean isCanUseTicketing() {
        return (flags & FLAG_CAN_USE_TICKETING) != 0;
    }

    public boolean isCanUseCICO() {
        return (flags & FLAG_CAN_USE_CICO) != 0;
    }

    public static TripAdvertisementData unwrap(byte[] serviceData) throws IOException {
        try (InputStream in = new ByteArrayInputStream(serviceData)) {
            return new TripAdvertisementData(in);
        }
    }

    public static byte[] wrap(TripAdvertisementData data) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            data.write(out);
            return out.toByteArray();
        }
    }

    private LocalTime convertTime(int bits) {
        final int mask = 2047;
        bits &= mask;
        if (bits == mask) {
            return null;
        }
        return LocalTime.ofSecondOfDay(bits * 60);
    }

    private int convertTime(LocalTime time) {
        if (time == null) {
            return 2047;
        }
        return time.toSecondOfDay() / 60;
    }

    private int readInt24(DataInputStream dis) throws IOException {
        int b1 = dis.readUnsignedByte();
        int b2 = dis.readUnsignedByte();
        int b3 = dis.readUnsignedByte();
        return (b1 << 16) | (b2 << 8) | b3;
    }

    private void writeInt24(DataOutputStream dos, int value) throws IOException {
        dos.writeByte((value >> 16) & 0xFF);
        dos.writeByte((value >> 8) & 0xFF);
        dos.writeByte(value & 0xFF);
    }

    @NonNull
    @Override
    public String toString() {
        return "TripAdvertisementData{" +
                "lineType=" + lineType +
                ", lineLicenseNumber=" + lineLicenseNumber +
                ", tripNumber=" + tripNumber +
                ", directionCisNumber=" + directionCisNumber +
                ", stopCisNumber=" + stopCisNumber +
                ", stopArrTime=" + stopArrTime +
                ", stopDepTime=" + stopDepTime +
                ", delay=" + delay +
                ", flags=" + flags +
                '}';
    }
}
