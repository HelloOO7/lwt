package cz.spojenka.lwt.util;

import android.bluetooth.le.ScanRecord;

/**
 * Some Android devices tend to do weird things to BLE scan records which render them unparseable.
 * For example, on a Sony XPERIA 10 III, when using {@link android.bluetooth.le.ScanSettings#CALLBACK_TYPE_FIRST_MATCH},
 * the scan record sometimes gets wrapped around with extra
 * bytes at the end, which causes the Android ScanRecord parser to throw an exception. Sometimes,
 * it does not happen. On a Xiaomi Redmi Note 8T, it does not happen at all.
 * This utility provides a way to operate over the raw scan record bytes, bypassing the Android
 * framework.
 */
public class BLEScanRecordUtil {

    public static byte[] getField(ScanRecord scanRec, int dataType) {
        byte[] data = scanRec.getBytes();
        int pos = 0;
        while (pos + 1 < data.length) {
            int length = data[pos] & 0xFF;
            int type = data[pos + 1] & 0xFF;
            if (type == dataType) {
                byte[] result = new byte[length - 1];
                System.arraycopy(data, pos + 2, result, 0, length - 1);
                return result;
            }
            pos += length + 1;
        }
        return null;
    }

    public static int getServiceUUID16(byte[] serviceData) {
        if (serviceData.length < 2) {
            throw new IllegalArgumentException("Service data too short to contain a 16-bit UUID");
        }
        return ((serviceData[1] & 0xFF) << 8) | (serviceData[0] & 0xFF);
    }

    public static int getServiceUUID32(byte[] serviceData) {
        if (serviceData.length < 4) {
            throw new IllegalArgumentException("Service data too short to contain a 32-bit UUID");
        }
        return ((serviceData[3] & 0xFF) << 24) | ((serviceData[2] & 0xFF) << 16) | ((serviceData[1] & 0xFF) << 8) | (serviceData[0] & 0xFF);
    }

    public static byte[] getServiceDataPayload(byte[] serviceData, int uuidLength) {
        if (serviceData.length < uuidLength) {
            throw new IllegalArgumentException("Service data too short to contain the specified UUID");
        }
        byte[] payload = new byte[serviceData.length - uuidLength];
        System.arraycopy(serviceData, uuidLength, payload, 0, payload.length);
        return payload;
    }
}
