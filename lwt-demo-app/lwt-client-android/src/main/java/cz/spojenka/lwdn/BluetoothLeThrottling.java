package cz.spojenka.lwdn;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cz.spojenka.lwdn.util.DeviceSpecifics;

public class BluetoothLeThrottling {

    private static final String PK_SCAN_START_TIMES = "scanStartTimes";
    private static final String PK_LAST_BOOT_COUNT = "lastBootCount";

    // huawei note: when screen is off, scans are limited to 5 per *hour*
    // this is fine because we only need to restart scans once every 30 minutes at worst,
    // as on Android 14, we can use low power scans without restarts
    private static final int RATE_LIMIT_PERIOD = 30 * 1000;
    private static final int RATE_LIMIT_MAX_SCANS = 5;

    private static final int SCAN_DOWNGRADE_TIMEOUT =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ? 10 * 60 * 1000  // Android 14 downgrades scans to LOW_POWER after 10 minutes
            : 30 * 60 * 1000; // Android 13 and below downgrades scans to OPPORTUNISTIC after 30 minutes

    private static final int SCAN_DOWNGRADE_MODE =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ? ScanSettings.SCAN_MODE_LOW_POWER
            : ScanSettings.SCAN_MODE_OPPORTUNISTIC;

    private static List<Long> scanStartTimes = null;
    private static Integer bootCount;

    private static boolean tryStartRateLimitedScan() {
        long ts = SystemClock.elapsedRealtime(); // do not use System.currentTimeMillis() here, because it can be changed by the user
        // remove old timestamps
        removeOldScanStartTimes(ts);
        if (scanStartTimes.size() >= RATE_LIMIT_MAX_SCANS) {
            return false;
        }
        return true;
    }

    private static void logScanStartTime() {
        scanStartTimes.add(SystemClock.elapsedRealtime());
    }

    private static void removeOldScanStartTimes(long ts) {
        scanStartTimes.removeIf(startTime -> ts - startTime >= RATE_LIMIT_PERIOD);
    }

    static void tryStartRateLimitedScan(Context context, Runnable scanProc, Runnable onFailed) {
        loadScanStartTimesIfNeeded(context);
        if (!tryStartRateLimitedScan()) {
            onFailed.run();
        } else {
            try {
                scanProc.run();
            } finally {
                // log after Android has logged the scan itself
                logScanStartTime();
                saveScanStartTimes(context);
            }
        }
    }

    private static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(BluetoothLeThrottling.class.getSimpleName(), Context.MODE_PRIVATE);
    }

    private static int getBootCount(Context context) {
        if (bootCount == null) {
            bootCount = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        }
        return bootCount;
    }

    private static void loadScanStartTimesIfNeeded(Context context) {
        if (scanStartTimes == null) {
            SharedPreferences prefs = getSharedPreferences(context);
            Set<String> times = prefs.getStringSet(PK_SCAN_START_TIMES, null);
            int bootCount = prefs.getInt(PK_LAST_BOOT_COUNT, -1);
            if (bootCount != getBootCount(context)) {
                times = null;
            }
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
            Set<String> times = new HashSet<>();
            for (Long time : scanStartTimes) {
                times.add(Long.toString(time));
            }
            prefs.edit()
                    .putStringSet(PK_SCAN_START_TIMES, times)
                    .putInt(PK_LAST_BOOT_COUNT, getBootCount(context))
                    .apply();
        }
    }

    public static long getNextUnthrottledScanTime(Context context) {
        loadScanStartTimesIfNeeded(context);
        long ts = SystemClock.elapsedRealtime();
        removeOldScanStartTimes(ts);
        if (scanStartTimes.size() < RATE_LIMIT_MAX_SCANS) {
            return ts;
        }
        long oldest = scanStartTimes.get(0);
        return oldest + RATE_LIMIT_PERIOD;
    }

    public static int getScanDowngradeTimeout(BluetoothAdapter adapter, ScanSettings settings, List<ScanFilter> filters) {
        if (settings.getScanMode() <= SCAN_DOWNGRADE_MODE) {
            return 0;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            // 3 filters are always reserved by the system
            // 10 filters au minimum are required for isOffloadedFilteringSupported() to return true
            // google play services sometimes runs an ambient scan that uses 3 filters.
            // we will therefore take a guess that 2 filters are always available, although
            // this may not actually be true in some edge cases
            if (adapter.isOffloadedFilteringSupported() && filters.size() <= 2) {
                // if the scan was mapped to offloaded filtering, Android will exempt it from
                // downgrading, see
                // https://cs.android.com/android/platform/superproject/+/android13-release:packages/modules/Bluetooth/android/app/src/com/android/bluetooth/gatt/ScanManager.java;l=873
                // this ONLY applies to Android 13 and below, Android 14 will downgrade scans regardless of offloaded filtering support,
                // but the downgrade is to low power mode (not opportunistic), which is far less destructive
                return 0;
            }
        }
        return SCAN_DOWNGRADE_TIMEOUT;
    }

    public static int getScanModeAfterDowngrade() {
        return SCAN_DOWNGRADE_MODE;
    }

    public static boolean isHuaweiConnectionThrottled(Throwable error) {
        if (DeviceSpecifics.isHuaweiOs() || DeviceSpecifics.isHonor()) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) {
                return error instanceof IOException && "Connect refused".equals(error.getMessage());
            }
        }
        return false;
    }

    public static long getHuaweiConnectionThrottlePeriod() {
        if (DeviceSpecifics.isHonor()) {
            // Honor became independent of huawei in 2020 and kept the old code
            // The 2-minute limit is still present on MagicOS 10
            return 120000;
        } else if (DeviceSpecifics.isHuaweiOs()) {
            // EMUI 13 (and newer Android 12-based releases) has these:
            // EXCESSIVE_CONNECTING_ALLOW_CONNECT_TIMER = 60000;
            // EXCESSIVE_CONNECTING_EXIST_CONTROL_TIMER = 120000;
            // EXCESSIVE_CONNECTING_NO_CONTROL_TIMER = 300000;
            //
            // Not sure about EMUI 16 yet, as that is currently only available on Pura 90s series
            // and there are no public firmware files available for download as of Sep 2026.
            //
            // Before EMUI 13, all of those were one constant, with value 120 000.
            // Once we get timed out, EXCESSIVE_CONNECTING_ALLOW_CONNECT_TIMER applies,
            // so the wait time was lowered.
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? 60000 : 120000;
        }
        throw new IllegalStateException("Called on non-Huawei device");
    }
}
