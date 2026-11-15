package cz.spojenka.lwdn;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.Nullable;
import cz.spojenka.lwdn.util.DeviceSpecifics;

public class BluetoothLeScan extends AbstractScan<ScanResult, Exception, BluetoothLeScan> implements AutoCloseable {

    private static final String TAG = BluetoothLeScan.class.getName();

    private final Context context;
    private final BluetoothAdapter adapter;
    private final ScanSettings settings;
    private final List<ScanFilter> filters;

    private final BroadcastReceiver bluetoothStateReceiver;
    private boolean isStarted = false;
    private boolean isRunning = false;

    private BluetoothLeScanner leScanner;
    private final TimeoutScanCallback scanCallback;

    private final Handler handler;
    private Runnable restartScanCallback;

    private final Duration scanTimeout;
    private long scanStartTime;
    private long scanEndTime;

    private BluetoothLeScan(Context context, ScanSettings settings, List<ScanFilter> filters, Duration scanTimeout) {
        this.context = context.getApplicationContext();
        adapter = getBluetoothAdapter(this.context);
        this.settings = settings;
        this.filters = filters;
        this.scanTimeout = scanTimeout;
        bluetoothStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (newState == BluetoothAdapter.STATE_ON) {
                    resumeScan();
                } else if (newState != BluetoothAdapter.STATE_TURNING_ON) {
                    pauseScan();
                }
            }
        };
        scanCallback = new TimeoutScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (!isFinished()) {
                    if (callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST) {
                        removeResult(result);
                    } else {
                        addResult(result);
                    }
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                markFailed(new BluetoothLeScanException(errorCode));
                stop();
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                if (!isFinished()) {
                    results.forEach(BluetoothLeScan.this::addResult);
                }
            }

            @Override
            protected void onTimedOut() {
                stop();
            }
        };
        handler = new Handler(Looper.getMainLooper());
    }

    public static @Nullable BluetoothAdapter getBluetoothAdapter(Context context) {
        BluetoothManager btm = context.getSystemService(BluetoothManager.class);
        if (btm != null) {
            BluetoothAdapter adapter = btm.getAdapter();
            if (adapter != null && isSupported(context)) {
                return adapter;
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
            }
            // on Android 10, the background location permission does exist,
            // and is needed to start LE scans in the background
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get package info", e);
        }
        return true;
    }

    public static boolean canStartInBackground(Context context) {
        if (isBackgroundLocationPermissionExists() && isBackgroundLocationPermissionNeeded(context)) {
            return context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    public static boolean isServiceUUIDFilteringBroken(Context context) {
        // not necessarily - it seems to work fine on a Huawei device running Pie
        // and a Samsung Galaxy S II running Q.
        // not so much on a Pie LG device and Q Xiaomi device, unfortunately.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            return true;
        }
        if (DeviceSpecifics.isXiaomi()) {
            BluetoothAdapter adapter = getBluetoothAdapter(context);
            return adapter != null && !adapter.isLeExtendedAdvertisingSupported();
        }
        return false;
    }

    public static boolean shouldUseSoftwareUUIDFiltering(Context context) {
        return isServiceUUIDFilteringBroken(context) || BluetoothCompatibilityMode.getInstance(context).isForcedSoftwareFiltering();
    }

    @Override
    protected boolean isResultAddressEqual(ScanResult res1, ScanResult res2) {
        return Objects.equals(res1.getDevice().getAddress(), res2.getDevice().getAddress());
    }

    public void addOnResultListener(OnResultListener listener) {
        addOnResultListenerImpl(listener);
    }

    public void addOnFinishedListener(OnFinishedListener listener) {
        addOnFinishedListenerImpl(listener);
    }

    public void removeOnResultListener(OnResultListener listener) {
        removeOnResultListenerImpl(listener);
    }

    public void removeOnFinishedListener(OnFinishedListener listener) {
        removeOnFinishedListenerImpl(listener);
    }

    public void start() {
        if (isFinished()) {
            throw new IllegalStateException("Scan has already finished");
        }
        if (isStarted) {
            return;
        }
        isStarted = true;
        context.registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        scanStartTime = SystemClock.elapsedRealtime();
        scanEndTime = scanTimeout != null ? scanStartTime + scanTimeout.toMillis() : 0;

        if (adapter.isEnabled()) {
            resumeScan();
        }

        if (scanTimeout != null) {
            scanCallback.startTimeout(scanTimeout);
        }
    }

    public void stop() {
        if (!isStarted) {
            return;
        }
        isStarted = false;
        context.unregisterReceiver(bluetoothStateReceiver);
        scanCallback.cancelTimeout();
        stopScanImpl();
        markFinished();
    }

    @Override
    public void close() {
        stop();
    }

    private void cancelRestartCallback() {
        if (restartScanCallback != null) {
            handler.removeCallbacks(restartScanCallback);
            restartScanCallback = null;
        }
    }

    private void resumeScan() {
        if (isStarted && !isRunning) {
            isRunning = true;
            startScanImpl();
        }
    }

    private void pauseScan() {
        if (isStarted && isRunning) {
            isRunning = false;
            stopScanImpl();
        }
    }

    private void startScanImpl() {
        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            markFailed(new UnsupportedOperationException("Bluetooth LE scanner is not supported (even though Bluetooth is enabled)"));
            return;
        }
        this.leScanner = scanner;
        try {
            long thisScanStartTime = SystemClock.elapsedRealtime();
            BluetoothLeScannerCompat.startScan(context, scanner, filters, settings, scanCallback);

            int systemScanTimeout = BluetoothLeThrottling.getScanDowngradeTimeout(adapter, settings, filters);
            if (systemScanTimeout != 0) {
                if (scanEndTime == 0 || thisScanStartTime + systemScanTimeout < scanEndTime) {
                    if (canStartInBackground(context)) {
                        handler.postDelayed(restartScanCallback = this::restartScan, (long) (systemScanTimeout * 0.95f));
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            Log.e(TAG, "Background location permission not granted. Scan will be downgraded to low-power mode after 10 minutes.");
                        } else {
                            Log.e(TAG, "Background location permission not granted. Scan will be downgraded to opportunistic mode after 30 minutes!!");
                        }
                    }
                }
            }
        } catch (SecurityException ex) {
            markFailed(ex);
        }
    }

    private void stopScanImpl() {
        if (leScanner == null) {
            return;
        }
        cancelRestartCallback();
        try {
            leScanner.stopScan(scanCallback);
            leScanner = null;
        } catch (IllegalStateException ex) {
            Log.e(TAG, "stopScan failed, assuming bluetooth off", ex);
            // adapter disabled by user, ignore
        } catch (SecurityException ex) {
            markFailed(ex);
        }
    }

    private void restartScan() {
        Log.d(TAG, "Restarting scan to avoid downgrade");
        if (!isFinished()) {
            stopScanImpl();
            startScanImpl();
        }
    }

    @Override
    protected void onCancel() {
        stop();
    }

    private class TimeoutScanCallback extends ScanCallback {

        private final Runnable timeoutRunnable = this::onTimedOut;

        public void startTimeout(Duration timeout) {
            handler.postDelayed(timeoutRunnable, timeout.toMillis());
        }

        public void cancelTimeout() {
            handler.removeCallbacks(timeoutRunnable);
        }

        protected void onTimedOut() {

        }
    }

    public static class Builder {

        private final Context context;
        private ScanSettings settings;
        private List<ScanFilter> filters = new ArrayList<>();
        private Duration scanTimeout;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setSettings(ScanSettings settings) {
            this.settings = settings;
            return this;
        }

        public Builder setFilters(List<ScanFilter> filters) {
            this.filters = filters;
            return this;
        }

        public Builder addFilter(ScanFilter filter) {
            filters.add(filter);
            return this;
        }

        public Builder setScanTimeout(Duration scanTimeout) {
            this.scanTimeout = scanTimeout;
            return this;
        }

        public BluetoothLeScan build() {
            return new BluetoothLeScan(context, settings, filters, scanTimeout);
        }
    }

    public static interface OnResultListener extends AbstractScan.OnResultListener<BluetoothLeScan, ScanResult, Exception> {

    }

    public static interface OnFinishedListener extends AbstractScan.OnFinishedListener<BluetoothLeScan, ScanResult, Exception> {

    }
}
