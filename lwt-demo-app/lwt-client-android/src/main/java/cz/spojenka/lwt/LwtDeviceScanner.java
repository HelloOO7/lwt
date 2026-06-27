package cz.spojenka.lwt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cz.spojenka.lwdn.HybridLwdnScanner;
import cz.spojenka.lwdn.LwdnScan;
import cz.spojenka.lwdn.LwdnScanConfig;
import cz.spojenka.lwdn.LwdnScanException;
import cz.spojenka.lwdn.LwdnScanResult;

public class LwtDeviceScanner {

    private static final String TAG = "LwtDeviceScanner";

    private final HybridLwdnScanner lwdnScanner = new HybridLwdnScanner();

    public LwtDeviceScanner(Context context) {
        BluetoothManager btm = context.getSystemService(BluetoothManager.class);
        if (btm != null) {
            BluetoothAdapter adapter = btm.getAdapter();
            if (adapter != null) {
                lwdnScanner.addBluetoothScanner(btm.getAdapter(), LwtServiceConstants.BLE_API_PSM);
            }
        }
    }

    public boolean isAvailable() {
        return lwdnScanner.isAvailable();
    }

    public LwtScan startScan() {
        return startScan(List.of(), new LwdnScanConfig.Builder().build());
    }

    public LwtScan startScan(LwdnScanConfig config) {
        return startScan(List.of(), config);
    }

    public LwtScan startScan(List<LwtDeviceType> deviceTypes, LwdnScanConfig config) {
        List<UUID> serviceUUIDs = new ArrayList<>();

        if (deviceTypes == null || deviceTypes.isEmpty()) {
            deviceTypes = List.of(LwtDeviceType.values());
        }

        for (LwtDeviceType deviceType : deviceTypes) {
            int uuid;
            if (lwdnScanner.isUsingExtendedAdvertising()) {
                uuid = LwtServiceConstants.serviceExtendedUUIDForDeviceType(deviceType);
            } else {
                uuid = LwtServiceConstants.serviceUUIDForDeviceType(deviceType);
            }
            serviceUUIDs.add(make32BitUUID(uuid));
        }

        LwdnScan lwdnScan = lwdnScanner.startScan(serviceUUIDs, config);
        LwtScan scan = new LwtScan();

        lwdnScan.addOnResultListener(new LwdnScan.OnResultListener() {
            @Override
            public void onResult(LwdnScan lwdnScan, LwdnScanResult result) {
                LwtDevice dev = createDeviceFromResult(result);
                if (dev != null) {
                    scan.addResult(dev);
                }
            }

            @Override
            public void onFailure(LwdnScan lwdnScan, LwdnScanException e) {
                // ignore - handle in onFinishedListener
            }
        });
        lwdnScan.addOnFinishedListener(new LwdnScan.OnFinishedListener() {
            @Override
            public void onFinished(LwdnScan lwdnScan) {
                scan.markFinished();
            }

            @Override
            public void onFailure(LwdnScan lwdnScan, LwdnScanException e) {
                scan.markFailed(e);
            }
        });

        return scan;
    }

    private LwtDevice createDeviceFromResult(LwdnScanResult result) {
        byte[] vehicleData = getVehicleResultData(result);
        if (vehicleData != null) {
            try {
                TripAdvertisementData advData;
                if (TripAdvertisementDataExt.isPresent(vehicleData)) {
                    advData = TripAdvertisementDataExt.unwrap(vehicleData);
                } else {
                    advData = TripAdvertisementData.unwrap(vehicleData);
                }
                return new LwtDevice.Vehicle(result.deviceAddress(), advData);
            } catch (IOException e) {
                Log.e(TAG, "Failed to parse vehicle advertisement data", e);
            }
        }
        return null;
    }

    private byte[] getVehicleResultData(LwdnScanResult result) {
        byte[] data = result.serviceData().get(make32BitUUID(LwtServiceConstants.BLE_SERVICE_UUID_VEHICLE));
        if (data == null) {
            data = result.serviceData().get(make32BitUUID(LwtServiceConstants.BLE_SERVICE_UUID_VEHICLE_EXTENDED));
        }
        return data;
    }

    private static UUID make32BitUUID(int value) {
        // https://stackoverflow.com/questions/13964342/android-how-do-bluetooth-uuids-work
        return new UUID((Integer.toUnsignedLong(value) << 32) | 0x1000, 0x800000805f9b34fbL);
    }
}
