package cz.spojenka.lwdn;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.RequiresPermission;

public class BluetoothLeScannerCompat {

    // the ScanCallback constant {@link ScanCallback#SCAN_FAILED_SCANNING_TOO_FREQUENTLY} is not available in Android 12 and below, so we define it here
    public static final int SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6;

    private static final int RATE_LIMIT_PERIOD = 30 * 1000;
    private static final int RATE_LIMIT_MAX_SCANS = 5;

    private static List<Long> scanStartTimes = null;

    private static boolean tryStartRateLimitedScan() {
        long ts = SystemClock.elapsedRealtime(); // do not use System.currentTimeMillis() here, because it can be changed by the user
        // remove old timestamps
        scanStartTimes.removeIf(startTime -> ts - startTime > RATE_LIMIT_PERIOD);
        if (scanStartTimes.size() >= RATE_LIMIT_MAX_SCANS) {
            return false;
        }
        scanStartTimes.add(ts);
        return true;
    }

    @SuppressLint("WrongConstant")
    private static void tryStartRateLimitedScan(Runnable scanProc, ScanCallback callback) {
        if (!tryStartRateLimitedScan()) {
            callback.onScanFailed(SCAN_FAILED_SCANNING_TOO_FREQUENTLY);
        } else {
            scanProc.run();
        }
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences("BluetoothLeScannerCompat", Context.MODE_PRIVATE);
    }

    private static void loadScanStartTimesIfNeeded(Context context) {
        if (scanStartTimes == null) {
            SharedPreferences prefs = getSharedPreferences(context);
            Set<String> times = prefs.getStringSet("scanStartTimes", null);
            if (times != null) {
                scanStartTimes = new ArrayList<>();
                for (String time : times) {
                    try {
                        scanStartTimes.add(Long.parseLong(time));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            } else {
                scanStartTimes = new ArrayList<>();
            }
        }
    }

    private static void saveScanStartTimes(Context context) {
        if (scanStartTimes != null) {
            SharedPreferences prefs = getSharedPreferences(context);
            Set<String> times = new java.util.HashSet<>();
            for (Long time : scanStartTimes) {
                times.add(Long.toString(time));
            }
            prefs.edit().putStringSet("scanStartTimes", times).apply();
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public static void startScan(Context context, BluetoothLeScanner scanner, List<ScanFilter> filters, ScanSettings settings, ScanCallback callback) {
        loadScanStartTimesIfNeeded(context);
        tryStartRateLimitedScan(() -> scanner.startScan(filters, settings, callback), callback);
        saveScanStartTimes(context);
    }
}
