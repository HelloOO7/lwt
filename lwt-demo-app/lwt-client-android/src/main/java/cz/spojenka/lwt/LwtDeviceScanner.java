package cz.spojenka.lwt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.annotation.Nullable;
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

    public LwtScan startScan(@Nullable List<LwtDeviceType> deviceTypes, LwdnScanConfig config) {
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
            serviceUUIDs.add(LwtScan.make32BitUUID(uuid));
        }

        LwdnScan lwdnScan = lwdnScanner.startScan(serviceUUIDs, config);

        return new LwtScan(lwdnScan);
    }
}
