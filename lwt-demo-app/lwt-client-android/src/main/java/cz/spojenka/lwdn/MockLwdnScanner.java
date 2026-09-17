package cz.spojenka.lwdn;

import android.os.Handler;
import android.os.Looper;

import java.time.Duration;
import java.util.List;

public class MockLwdnScanner implements LwdnScanner {

    private final LwdnMockClient client;
    private final Handler handler;

    public MockLwdnScanner(LwdnMockClient client) {
        this.client = client;
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public LwdnScan startScan(List<LwdnServiceID> services, LwdnScanConfig config) {
        LwdnScan scan = new LwdnScan();
        DeviceLostTimeoutManager timeoutManager = new DeviceLostTimeoutManager(handler, scan);
        LwdnMockClient.Observer clientObserver = new LwdnMockClient.Observer() {
            @Override
            public void onTimescaleChanged(float newTimescale) {
                timeoutManager.changePendingTimeouts(getScaledTimeout(config));
            }
        };
        client.addObserver(clientObserver);
        scan.addOnResultListener(new LwdnScan.OnResultListener() {
            @Override
            public void onResult(LwdnScan scan, LwdnScanResult result) {
                timeoutManager.updateDeviceLostTimeout(result.deviceAddress(), getScaledTimeout(config));
            }

            @Override
            public void onFailure(LwdnScan scan, LwdnScanException e) {

            }
        });
        scan.addOnFinishedListener(new LwdnScan.OnFinishedListener() {
            @Override
            public void onFinished(LwdnScan scan) {
                client.removeObserver(clientObserver);
                timeoutManager.cancelPendingTimeouts();
            }

            @Override
            public void onFinishedExceptionally(LwdnScan scan, LwdnScanException e) {
                onFinished(scan);
            }
        });
        client.startMockDeviceScan(services, scan);
        scan.setCancellationHandler(() -> client.stopMockDeviceScan(scan));
        return scan;
    }

    private Duration getScaledTimeout(LwdnScanConfig config) {
        Duration base = config.getDeviceLostTimeout();
        if (base == null) {
            return base;
        }
        float timescale = client.getCurrentTimescale();
        if (timescale == 0) {
            return null;
        }
        long millis = base.toMillis();
        millis = (long) (millis / timescale);
        return Duration.ofMillis(millis);
    }
}
