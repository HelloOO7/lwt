package cz.spojenka.lwt.demoapp;

import android.app.Application;
import android.os.SystemClock;
import android.util.Log;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cz.spojenka.lwdn.LwdnScanConfig;
import cz.spojenka.lwdn.LwdnScanException;
import cz.spojenka.lwdn.ScanErrorCode;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.LwtDeviceScanner;
import cz.spojenka.lwt.LwtDeviceType;
import cz.spojenka.lwt.LwtScan;

public class GlobalLwtScanner {

    private static final String TAG = "GlobalLwtScanner";

    private static final int RESULT_RETENTION_TIME = 30 * 1000;

    private static GlobalLwtScanner INSTANCE;

    private final LwtDeviceScanner scanner;

    private final Map<List<LwtDeviceType>, ActiveScan> activeScans = new HashMap<>();
    private final Map<List<LwtDeviceType>, ActiveScan> scanHistory = new HashMap<>();

    private GlobalLwtScanner(Application app) {
        this.scanner = new LwtDeviceScanner(app);
    }

    public static GlobalLwtScanner getInstance(Application app) {
        if (INSTANCE == null) {
            INSTANCE = new GlobalLwtScanner(app);
        }
        return INSTANCE;
    }

    public synchronized void cancelAllScans() {
        for (ActiveScan scan : activeScans.values()) {
            scan.scan().cancel();
        }
        activeScans.clear();
    }

    public synchronized void cancelScan(List<LwtDeviceType> deviceTypes) {
        ActiveScan scan = activeScans.remove(deviceTypes);
        if (scan != null) {
            scan.scan().cancel();
        }
    }

    public synchronized LwtScan scan(List<LwtDeviceType> deviceTypes) {
        return scan(deviceTypes, false);
    }

    public synchronized LwtScan scan(List<LwtDeviceType> deviceTypes, boolean continuous) {
        long ts = SystemClock.elapsedRealtime();
        ActiveScan activeScan = activeScans.get(deviceTypes);
        if (activeScan != null) {
            if ((ts - activeScan.timestamp() < RESULT_RETENTION_TIME || (continuous && !activeScan.scan().isFinished())) && (activeScan.isContinuous == continuous)) {
                return activeScan.scan();
            }
            activeScan.scan().cancel();
        }
        LwdnScanConfig.Builder config = new LwdnScanConfig.Builder();
        if (continuous) {
            // explicitly set infinite timeout, default is 10 seconds
            config.setTimeout(null);
        } else {
            config.setTimeout(Duration.ofSeconds(5));
        }
        LwtScan scan = scanner.startScan(deviceTypes, config.build());
        ActiveScan scanRecord = new ActiveScan(deviceTypes, ts, scan, continuous);

        activeScans.put(deviceTypes, scanRecord);

        return createScanWrapper(scanRecord);
    }

    private LwtScan createScanWrapper(ActiveScan baseScan) {
        Runnable addScanToHistory = () -> {
            ActiveScan history = scanHistory.get(baseScan.deviceTypes());
            if (history == null || history.timestamp() < baseScan.timestamp()) {
                scanHistory.put(baseScan.deviceTypes(), baseScan);
            }
        };

        baseScan.scan().addOnResultListener(new LwtScan.OnResultListener() {
            @Override
            public void onResult(LwtScan scan, LwtDevice result) {
                // register as soon as we see the first result
                addScanToHistory.run();
            }

            @Override
            public void onFailure(LwtScan scan, LwdnScanException e) {

            }
        });
        baseScan.scan().addOnFinishedListener(new LwtScan.OnFinishedListener() {
            @Override
            public void onFinished(LwtScan scan) {
                Log.i(TAG, "Scan finished successfully");
                if (scan.wasCancelled()) {
                    activeScans.remove(baseScan.deviceTypes(), baseScan);
                }
                addScanToHistory.run(); // in case there were no results, we still want to register the scan in history
            }

            @Override
            public void onFinishedExceptionally(LwtScan scan, LwdnScanException e) {
                Log.e(TAG, "Scan finished exceptionally", e);
                activeScans.remove(baseScan.deviceTypes(), baseScan);
            }
        });

        return LwtScan.map(baseScan.scan(), new LwtScan.ScanMapper() {
            @Override
            public void mapFinishedExceptionally(LwtScan scan, LwdnScanException e, LwtScan destScan) {
                if (baseScan.isContinuous() && e.getCode() == ScanErrorCode.THROTTLED) {
                    ActiveScan last = scanHistory.get(baseScan.deviceTypes());
                    if (last != null) {
                        copyResults(last.scan(), destScan);
                        markFinished(destScan);
                        return;
                    }
                }
                super.mapFinishedExceptionally(scan, e, destScan);
            }
        });
    }

    private static record ActiveScan(
            List<LwtDeviceType> deviceTypes,
            long timestamp,
            LwtScan scan,
            boolean isContinuous
    ) {

    }
}
