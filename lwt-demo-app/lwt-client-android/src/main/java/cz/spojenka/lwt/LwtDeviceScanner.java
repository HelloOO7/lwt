package cz.spojenka.lwt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.net.wifi.aware.WifiAwareManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.annotation.Nullable;
import cz.spojenka.lwdn.BluetoothLwdnScanner;
import cz.spojenka.lwdn.HybridLwdnScanner;
import cz.spojenka.lwdn.LwdnScan;
import cz.spojenka.lwdn.LwdnScanConfig;
import cz.spojenka.lwdn.LwdnScanException;
import cz.spojenka.lwdn.LwdnScanResult;
import cz.spojenka.lwdn.LwdnServiceID;

public class LwtDeviceScanner {

    private static final String TAG = "LwtDeviceScanner";

    private final HybridLwdnScanner lwdnScanner = new HybridLwdnScanner();

    public LwtDeviceScanner(Context context, LwtLinkSession session) {
        BluetoothManager btm = context.getSystemService(BluetoothManager.class);
        if (btm != null) {
            BluetoothAdapter adapter = btm.getAdapter();
            if (adapter != null && BluetoothLwdnScanner.isSupported(context)) {
                lwdnScanner.addBluetoothScanner(context, btm.getAdapter(), LwtServiceConstants.BLE_API_PSM);
            }
        }
        WifiAwareManager wam = context.getSystemService(WifiAwareManager.class);
        if (wam != null) {
            lwdnScanner.addWifiAwareScanner(wam, session.getAwareSessionManager(wam), LwtServiceConstants.WIFI_API_PORT);
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
        List<LwdnServiceID> serviceIDs = new ArrayList<>();

        if (deviceTypes == null || deviceTypes.isEmpty()) {
            deviceTypes = List.of(LwtDeviceType.values());
        }

        for (LwtDeviceType deviceType : deviceTypes) {
            serviceIDs.add(LwtServiceConstants.serviceNameForDeviceType(deviceType));
            if (lwdnScanner.isUsingExtendedAdvertising()) {
                serviceIDs.add(LwtServiceConstants.serviceExtendedUUIDForDeviceType(deviceType));
            } else {
                serviceIDs.add(LwtServiceConstants.serviceUUIDForDeviceType(deviceType));
            }
        }

        LwdnScan lwdnScan = lwdnScanner.startScan(serviceIDs, config);

        return new LwtScan(lwdnScan);
    }
}
