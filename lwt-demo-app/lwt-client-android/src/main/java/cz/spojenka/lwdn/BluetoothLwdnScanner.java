package cz.spojenka.lwdn;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import androidx.annotation.Nullable;
import cz.spojenka.lwdn.util.DeviceSpecifics;
import cz.spojenka.lwdn.util.XiaomiBLERestrictionKiller;

public class BluetoothLwdnScanner implements LwdnScanner {

    private static final String TAG = BluetoothLwdnScanner.class.getName();

    private final Context context;
    private final BluetoothAdapter adapter;
    private final int addressPsm;
    private final boolean isUsingExtendedAdvertising;

    private final Handler handler;

    public BluetoothLwdnScanner(Context context, BluetoothAdapter adapter, int addressPsm) {
        this.context = context;
        this.adapter = adapter;
        this.addressPsm = addressPsm;
        isUsingExtendedAdvertising = adapter.isLeExtendedAdvertisingSupported();
        handler = new Handler(Looper.getMainLooper());
    }

    public static @Nullable BluetoothLwdnScanner create(Context context, int addressPsm) {
        BluetoothManager btm = context.getSystemService(BluetoothManager.class);
        if (btm != null) {
            BluetoothAdapter adapter = btm.getAdapter();
            if (adapter != null && isSupported(context)) {
                return new BluetoothLwdnScanner(context, adapter, addressPsm);
            }
        }
        return null;
    }

    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private static boolean isBackgroundLocationPermissionExists() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    public static boolean isBackgroundLocationPermissionNeeded(Context context) {
        if (!isBackgroundLocationPermissionExists()) {
            // no background location permission exists on Android 9 and below
            return true;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
                if (pi.requestedPermissions != null && pi.requestedPermissionsFlags != null) {
                    for (int i = 0; i < pi.requestedPermissions.length; i++) {
                        if (Manifest.permission.BLUETOOTH_SCAN.equals(pi.requestedPermissions[i]) &&
                                (pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION) != 0) {
                            return false;
                        }
                    }
                }
            } else {
                // on Android 10, the background location permission does exist,
                // but it is not needed for Bluetooth scanning, as Manifest.permission.BLUETOOTH_SCAN does not exist until
                // Android 11
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get package info", e);
        }
        return true;
    }

    public boolean canStartInBackground() {
        if (isBackgroundLocationPermissionExists() && isBackgroundLocationPermissionNeeded(context)) {
            return context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    @Override
    public boolean isUsingExtendedAdvertising() {
        return isUsingExtendedAdvertising;
    }

    @Override
    public boolean isAvailable() {
        return adapter.isEnabled();
    }

    public static boolean isServiceUUIDFilteringBroken() {
        // not necessarily - it seems to work fine on a Huawei device running Pie
        // and a Samsung Galaxy S II running Q.
        // not so much on a Pie LG device and Q Xiaomi device, unfortunately.
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q || DeviceSpecifics.isXiaomi();
    }

    private boolean shouldUseSoftwareUUIDFiltering() {
        return isServiceUUIDFilteringBroken() || BluetoothCompatibilityMode.getInstance(context).isForcedSoftwareFiltering();
    }

    @Override
    public LwdnScan startScan(List<LwdnServiceID> services, LwdnScanConfig config) {
        LwdnScan scan = new LwdnScan();

        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();

        if (scanner != null) {
            new ScanController(scanner, scan).startScan(services, config);
        } else {
            if (adapter.isEnabled()) {
                scan.markFailed(new LwdnScanException(ScanErrorCode.NOT_SUPPORTED, "Bluetooth LE scanner is not supported (even though Bluetooth is enabled)"));
            } else {
                scan.markFailed(new LwdnScanException(ScanErrorCode.NOT_ENABLED, "Bluetooth is off"));
            }
        }

        return scan;
    }

    private class TimeoutScanCallback extends ScanCallback {

        public void startTimeout(Duration timeout) {
            handler.postDelayed(this::onTimedOut, timeout.toMillis());
        }

        protected void onTimedOut() {

        }
    }

    private class ScanController {

        private final BluetoothLeScanner scanner;
        private TimeoutScanCallback callback;
        private final LwdnScan scan;

        private final Map<BluetoothLwdnAddress, Runnable> deviceLostTimeoutCallbacks = new HashMap<>();

        private long scanStartTime;
        private long scanEndTime;

        public ScanController(BluetoothLeScanner scanner, LwdnScan scan) {
            this.scanner = scanner;
            this.scan = scan;
            scan.setCancellationHandler(this::stopScan);
        }

        public void startScan(List<LwdnServiceID> services, LwdnScanConfig config) {
            Predicate<ScanResult> softwareFilter = buildSoftwareScanFilterIfNeeded(services);

            callback = new TimeoutScanCallback() {

                @Override
                public synchronized void onScanResult(int callbackType, ScanResult result) {
                    if (isUsingExtendedAdvertising && result.isLegacy()) {
                        // huawei (on Pie) returns legacy advertisements even when scanning for extended advertisements,
                        // (despite the fact that this is not supposed to happen)
                        return;
                    }
                    if (!softwareFilter.test(result)) {
                        return;
                    }
                    Log.d("BluetoothLwdnScanner", "onScanResult: " + result.getDevice().getAddress() + " rssi=" + result.getRssi());
                    if (scan.isFinished()) {
                        // result after timeout
                        return;
                    }

                    BluetoothLwdnAddress address = new BluetoothLwdnAddress(result.getDevice(), addressPsm);

                    if (callbackType != ScanSettings.CALLBACK_TYPE_MATCH_LOST) {
                        if (scan.getResultCount() < config.getMaxDevices()) {
                            if (result.getScanRecord() != null) {
                                Map<LwdnServiceID, byte[]> serviceData = new HashMap<>();
                                for (var e : result.getScanRecord().getServiceData().entrySet()) {
                                    serviceData.put(new LwdnServiceID.UUID(e.getKey().getUuid()), e.getValue());
                                }
                                scan.addResult(new LwdnScanResult(address, result.getRssi(), serviceData));
                            }
                            updateDeviceLostTimeout(address, config);
                        }
                        if (scan.getResultCount() >= config.getMaxDevices()) {
                            stopScan();
                        }
                    } else {
                        scan.removeResult(new LwdnScanResult(address, 0, Map.of()));
                    }
                }

                @Override
                protected void onTimedOut() {
                    stopScan();
                }

                @Override
                public void onScanFailed(int errorCode) {
                    scan.markFailed(new LwdnScanException(switch (errorCode) {
                        case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                                ScanErrorCode.NOT_SUPPORTED;
                        case ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
                                ScanErrorCode.ALREADY_RUNNING;
                        case BluetoothLeScannerCompat.SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                                ScanErrorCode.THROTTLED;
                        case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
                                ScanErrorCode.OUT_OF_RESOURCES;
                        default -> ScanErrorCode.INTERNAL_ERROR;
                    }));
                }
            };

            ScanSettings settings = buildScanSettings(config);
            List<ScanFilter> filters = buildScanFilters(services);

            scanStartTime = SystemClock.elapsedRealtime();
            scanEndTime = config.getTimeout() != null ? scanStartTime + config.getTimeout().toMillis() : 0;

            if (startScanImpl(settings, filters)) {
                if (config.getTimeout() != null) {
                    callback.startTimeout(config.getTimeout());
                }
            }
        }

        private boolean startScanImpl(ScanSettings settings, List<ScanFilter> filters) {
            try {
                //XiaomiBLERestrictionKiller.apply();
                long thisScanStartTime = SystemClock.elapsedRealtime();
                BluetoothLeScannerCompat.startScan(context, scanner, filters, settings, callback);

                int systemScanTimeout = BluetoothLeThrottling.getScanDowngradeTimeout(adapter, settings, filters);
                if (systemScanTimeout != 0) {
                    if (scanEndTime == 0 || thisScanStartTime + systemScanTimeout < scanEndTime) {
                        if (canStartInBackground()) {
                            handler.postDelayed(() -> restartScan(settings, filters), (long) (systemScanTimeout * 0.95f));
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                Log.e(TAG, "Background location permission not granted. Scan will be downgraded to low-power mode after 10 minutes.");
                            } else {
                                Log.e(TAG, "Background location permission not granted. Scan will be downgraded to opportunistic mode after 30 minutes!!");
                            }
                        }
                    }
                }

                return true;
            } catch (SecurityException ex) {
                scan.markFailed(new LwdnScanException(ScanErrorCode.NOT_PERMITTED, ex));
                return false;
            }
        }

        private void restartScan(ScanSettings settings, List<ScanFilter> filters) {
            Log.d(TAG, "Restarting scan to avoid downgrade");
            if (!scan.isFinished()) {
                stopScanImpl();
                startScanImpl(settings, filters);
            }
        }

        private void updateDeviceLostTimeout(BluetoothLwdnAddress deviceAddress, LwdnScanConfig config) {
            if (config.getDeviceLostTimeout() == null) {
                return;
            }
            Runnable currentCallback = deviceLostTimeoutCallbacks.get(deviceAddress);
            if (currentCallback != null) {
                handler.removeCallbacks(currentCallback);
            }
            Runnable newCallback = () -> {
                scan.removeResult(new LwdnScanResult(deviceAddress, 0, Map.of()));
                deviceLostTimeoutCallbacks.remove(deviceAddress);
            };
            deviceLostTimeoutCallbacks.put(deviceAddress, newCallback);
            handler.postDelayed(newCallback, config.getDeviceLostTimeout().toMillis());
        }

        private ScanSettings buildScanSettings(LwdnScanConfig config) {
            ScanSettings.Builder settings = new ScanSettings.Builder()
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setScanMode(
                            config.getScanMode() == LwdnScanConfig.ScanMode.LOW_LATENCY
                                    ? ScanSettings.SCAN_MODE_LOW_LATENCY
                                    : ScanSettings.SCAN_MODE_LOW_POWER
                    )
                    .setLegacy(!isUsingExtendedAdvertising);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                settings.setRssiThreshold(config.getMinRssi());
            }
            return settings.build();
        }

        private List<ScanFilter> buildScanFilters(List<LwdnServiceID> services) {
            List<ScanFilter> filters = new ArrayList<>();
            for (LwdnServiceID serviceId : services) {
                if (!shouldUseSoftwareUUIDFiltering()) {
                    if (serviceId instanceof LwdnServiceID.UUID serviceUUID) {
                        filters.add(
                                new ScanFilter.Builder()
                                        // data+mask is needed (even though frontend allows a null value), because otherwise
                                        // the filter is ignored further down the BT stack
                                        .setServiceData(new ParcelUuid(serviceUUID.uuid()), new byte[0], new byte[0])
                                        .build()
                        );
                    }
                } else {
                    if (serviceId instanceof LwdnServiceID.DeviceName deviceName) {
                        filters.add(
                                new ScanFilter.Builder()
                                        .setDeviceName(deviceName.name())
                                        .build()
                        );
                    }
                }
            }
            if (filters.isEmpty()) {
                throw new IllegalArgumentException("No compatible service IDs were provided for bluetooth scan, must give at least 1 UUID-type service ID.");
            }
            return filters;
        }

        private Predicate<ScanResult> buildSoftwareScanFilterIfNeeded(List<LwdnServiceID> services) {
            if (!shouldUseSoftwareUUIDFiltering()) {
                return result -> true;
            } else {
                return buildSoftwareScanFilter(services);
            }
        }

        private static Predicate<ScanResult> buildSoftwareScanFilter(List<LwdnServiceID> services) {
            Set<ParcelUuid> serviceUUIDs = new HashSet<>();
            for (LwdnServiceID serviceId : services) {
                if (serviceId instanceof LwdnServiceID.UUID serviceUUID) {
                    serviceUUIDs.add(new ParcelUuid(serviceUUID.uuid()));
                }
            }
            if (serviceUUIDs.isEmpty()) {
                return result -> true;
            }
            return result -> {
                if (result.getScanRecord() != null) {
                    for (var service : result.getScanRecord().getServiceData().keySet()) {
                        if (serviceUUIDs.contains(service)) {
                            return true;
                        }
                    }
                }
                return false;
            };
        }

        public void stopScan() {
            stopScanImpl();
            scan.markFinished();
        }

        private void stopScanImpl() {
            try {
                try {
                    scanner.stopScan(callback);
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "stopScan failed, assuming bluetooth off", ex);
                    // adapter disabled by user, ignore
                }
            } catch (SecurityException ex) {
                scan.markFailed(new LwdnScanException(ScanErrorCode.NOT_PERMITTED, ex));
            }
        }
    }
}
