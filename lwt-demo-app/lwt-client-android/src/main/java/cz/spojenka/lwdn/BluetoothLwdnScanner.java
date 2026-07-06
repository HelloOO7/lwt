package cz.spojenka.lwdn;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
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

public class BluetoothLwdnScanner implements LwdnScanner {

    private final Context context;
    private final BluetoothAdapter adapter;
    private final BluetoothLeScanner scanner;
    private final int addressPsm;
    private final boolean isUsingExtendedAdvertising;

    private final Handler handler;

    public BluetoothLwdnScanner(Context context, BluetoothAdapter adapter, int addressPsm) {
        this.context = context;
        this.adapter = adapter;
        this.scanner = adapter.getBluetoothLeScanner();
        this.addressPsm = addressPsm;
        isUsingExtendedAdvertising = adapter.isLeExtendedAdvertisingSupported();
        handler = new Handler(Looper.getMainLooper());
    }

    public boolean isUsingExtendedAdvertising() {
        return isUsingExtendedAdvertising;
    }

    private List<ScanFilter> buildScanFilters(List<UUID> services) {
        List<ScanFilter> filters = new ArrayList<>();
        for (UUID serviceUUID : services) {
            filters.add(
                    new ScanFilter.Builder()
                            // data+mask is needed (even though frontend allows a null value), because otherwise
                            // the filter is ignored further down the BT stack
                            .setServiceData(new ParcelUuid(serviceUUID), new byte[0], new byte[0])
                            .build()
            );
        }
        return filters;
    }

    @Override
    public boolean isAvailable() {
        return adapter.isEnabled();
    }

    @SuppressLint("MissingPermission")
    @Override
    public LwdnScan startScan(List<UUID> services, LwdnScanConfig config) {
        LwdnScan scan = new LwdnScan();
        TimeoutScanCallback callback = new TimeoutScanCallback() {

            @Override
            public synchronized void onScanResult(int callbackType, ScanResult result) {
                if (scan.isFinished()) {
                    // result after timeout
                    return;
                }

                if (scan.getResultCount() < config.getMaxDevices()) {
                    if (result.getScanRecord() != null) {
                        Map<UUID, byte[]> serviceData = new HashMap<>();
                        for (var e : result.getScanRecord().getServiceData().entrySet()) {
                            serviceData.put(e.getKey().getUuid(), e.getValue());
                        }
                        scan.addResult(new LwdnScanResult(new BluetoothLwdnAddress(result.getDevice(), addressPsm), result.getRssi(), serviceData));
                    }
                }
                if (scan.getResultCount() >= config.getMaxDevices()) {
                    scan.cancel();
                }
            }

            @Override
            protected void onTimedOut() {
                scan.cancel();
            }

            @Override
            public void onScanFailed(int errorCode) {
                scan.markFailed(new LwdnScanException(switch (errorCode) {
                    case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                            ScanErrorCode.NOT_SUPPORTED;
                    case ScanCallback.SCAN_FAILED_ALREADY_STARTED -> ScanErrorCode.ALREADY_RUNNING;
                    case BluetoothLeScannerCompat.SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                            ScanErrorCode.THROTTLED;
                    case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
                            ScanErrorCode.OUT_OF_RESOURCES;
                    default -> ScanErrorCode.INTERNAL_ERROR;
                }));
            }
        };
        scan.setCancellationHandler(() -> {
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
        });

        try {
            ScanSettings.Builder settings = new ScanSettings.Builder()
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setLegacy(!isUsingExtendedAdvertising);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                settings.setRssiThreshold(config.getMinRssi());
            }

            BluetoothLeScannerCompat.startScan(context, scanner, buildScanFilters(services), settings.build(), callback);
            if (config.getTimeout() != null) {
                callback.startTimeout(config.getTimeout());
            }
        } catch (SecurityException ex) {
            scan.markFailed(new LwdnScanException(ScanErrorCode.NOT_PERMITTED, ex));
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
}
