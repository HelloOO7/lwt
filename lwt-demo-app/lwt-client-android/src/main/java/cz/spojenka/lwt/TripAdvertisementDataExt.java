package cz.spojenka.lwt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    @Override
    public void write(OutputStream out) throws IOException {
        super.write(out);
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeShort(DATA_MARK);
        writeString(dos, currentStopName);
        writeString(dos, lineName);
        writeString(dos, headsign);
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
        return out.toString("UTF-8"); //Charset method requires newer API level
    }

    private static void writeString(OutputStream out, String str) throws IOException {
        out.write(str.getBytes(StandardCharsets.UTF_8));
        out.write(0); // null terminator
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
