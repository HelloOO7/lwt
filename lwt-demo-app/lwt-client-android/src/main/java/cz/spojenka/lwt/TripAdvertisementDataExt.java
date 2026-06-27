package cz.spojenka.lwt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class TripAdvertisementDataExt extends TripAdvertisementData {

    private static final int DATA_MARK = 0x4544; // "ED"

    private final String currentStopName;
    private final String lineName;
    private final String headsign;

    public TripAdvertisementDataExt(InputStream in) throws IOException {
        super(in);
        DataInputStream dis = new DataInputStream(in);
        if (dis.readUnsignedShort() != DATA_MARK) {
            throw new IOException("Invalid data mark");
        }
        currentStopName = readString(dis);
        lineName = readString(dis);
        headsign = readString(dis);
    }

    public static boolean isPresent(byte[] data) {
        if (data.length >= TripAdvertisementData.BYTES + 2) {
            int off = TripAdvertisementData.BYTES;
            // big endian
            return ((Byte.toUnsignedInt(data[off]) << 8) | Byte.toUnsignedInt(data[off + 1])) == DATA_MARK;
        }
        return false;
    }

    private static String readString(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == 0) {
                break;
            }
            out.write(b);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public String getCurrentStopName() {
        return currentStopName;
    }

    public String getLineName() {
        return lineName;
    }

    public String getHeadsign() {
        return headsign;
    }

    public static TripAdvertisementDataExt unwrap(byte[] serviceData) throws IOException {
        try (InputStream in = new ByteArrayInputStream(serviceData)) {
            return new TripAdvertisementDataExt(in);
        }
    }

    @Override
    public String toString() {
        return "TripAdvertisementDataExt{" +
                "currentStopName='" + currentStopName + '\'' +
                ", lineName='" + lineName + '\'' +
                ", headsign='" + headsign + '\'' +
                "} " + super.toString();
    }
}
