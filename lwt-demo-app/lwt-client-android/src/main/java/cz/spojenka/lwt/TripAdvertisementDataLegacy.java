package cz.spojenka.lwt;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;

import androidx.annotation.NonNull;

public class TripAdvertisementDataLegacy {

    public static final int BYTES = 20;

    private static final int FLAG_IS_AT_STOP = 1;

    private final int lineType;
    private final int lineLicenseNumber;
    private final int tripNumber;
    private final int directionCisNumber;

    private final int stopCisNumber;
    private final LocalTime stopArrTime;
    private final LocalTime stopDepTime;
    private final int delay;
    private final int flags;

    public TripAdvertisementDataLegacy(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        lineType = dis.readUnsignedByte(); // +0x0
        lineLicenseNumber = readInt24(dis); // +0x1
        tripNumber = readInt24(dis); // +0x4
        directionCisNumber = dis.readInt(); // +0x7
        stopCisNumber = dis.readInt(); // +0xB
        int timeUnion = dis.readInt(); // +0xF
        stopArrTime = convertTime(timeUnion);
        stopDepTime = convertTime(timeUnion >> 11);
        delay = timeUnion >> 22;
        flags = dis.readUnsignedByte(); // +0x13
        // total 0x14 = 20 bytes
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

    public int getTripNumber() {
        return tripNumber;
    }

    public int getDirectionCisNumber() {
        return directionCisNumber;
    }

    public int getStopCisNumber() {
        return stopCisNumber;
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

    public static TripAdvertisementDataLegacy unwrap(byte[] serviceData) throws IOException {
        try (InputStream in = new ByteArrayInputStream(serviceData)) {
            return new TripAdvertisementDataLegacy(in);
        }
    }

    private LocalTime convertTime(int bits) {
        return LocalTime.ofSecondOfDay((bits & 2047) * 60);
    }

    private int readInt24(DataInputStream dis) throws IOException {
        int b1 = dis.readUnsignedByte();
        int b2 = dis.readUnsignedByte();
        int b3 = dis.readUnsignedByte();
        return (b1 << 16) | (b2 << 8) | b3;
    }

    @NonNull
    @Override
    public String toString() {
        return "TripAdvertisementDataLegacy{" +
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
