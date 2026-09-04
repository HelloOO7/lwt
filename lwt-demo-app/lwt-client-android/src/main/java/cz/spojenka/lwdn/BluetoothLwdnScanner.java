package cz.spojenka.lwdn;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.Nullable;
import cz.spojenka.lwt.LwtServiceConstants;

public class BluetoothLwdnScanner implements LwdnScanner {

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

    @Override
    public boolean isUsingExtendedAdvertising() {
        return isUsingExtendedAdvertising;
    }

    private static List<ScanFilter> buildScanFilters(List<LwdnServiceID> services) {
        List<ScanFilter> filters = new ArrayList<>();
        for (LwdnServiceID serviceId : services) {
            if (serviceId instanceof LwdnServiceID.UUID serviceUUID) {
                filters.add(
                        new ScanFilter.Builder()
                                // data+mask is needed (even though frontend allows a null value), because otherwise
                                // the filter is ignored further down the BT stack
                                .setServiceData(new ParcelUuid(serviceUUID.uuid()), new byte[0], new byte[0])
                                .build()
                );
            }
        }
        if (filters.isEmpty()) {
            throw new IllegalArgumentException("No compatible service IDs were provided for bluetooth scan, must give at least 1 UUID-type service ID.");
        }
        return filters;
    }

    @Override
    public boolean isAvailable() {
        return adapter.isEnabled();
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

        public ScanController(BluetoothLeScanner scanner, LwdnScan scan) {
            this.scanner = scanner;
            this.scan = scan;
            scan.setCancellationHandler(this::stopScan);
        }

        public void startScan(List<LwdnServiceID> services, LwdnScanConfig config) {
            callback = new TimeoutScanCallback() {

                @Override
                public synchronized void onScanResult(int callbackType, ScanResult result) {
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

            try {
                ScanSettings.Builder settings = buildScanSettings(config);
                BluetoothLeScannerCompat.startScan(context, scanner, buildScanFilters(services), settings.build(), callback);

                if (config.getTimeout() != null) {
                    callback.startTimeout(config.getTimeout());
                }
            } catch (SecurityException ex) {
                scan.markFailed(new LwdnScanException(ScanErrorCode.NOT_PERMITTED, ex));
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

        private ScanSettings.Builder buildScanSettings(LwdnScanConfig config) {
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
            return settings;
        }

        public void stopScan() {
            try {
                try {
                    scanner.stopScan(callback);
                } catch (IllegalStateException ignored) {
                    // adapter disabled by user, ignore
                }
                scan.markFinished();
            } catch (SecurityException ex) {
                scan.markFailed(new LwdnScanException(ScanErrorCode.NOT_PERMITTED, ex));
            }
        }
    }
}
