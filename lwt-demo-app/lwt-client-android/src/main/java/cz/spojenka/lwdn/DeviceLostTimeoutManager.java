package cz.spojenka.lwdn;

import android.os.Handler;
import android.os.SystemClock;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

class DeviceLostTimeoutManager {

    private final Handler handler;
    private final LwdnScan scan;

    private final Map<LwdnAddress, TimeoutRecord> deviceLostTimeoutCallbacks = new HashMap<>();

    public DeviceLostTimeoutManager(Handler handler, LwdnScan scan) {
        this.handler = handler;
        this.scan = scan;
    }

    public void updateDeviceLostTimeout(LwdnAddress deviceAddress, Duration timeout) {
        if (timeout == null) {
            return;
        }
        TimeoutRecord currentCallback = deviceLostTimeoutCallbacks.get(deviceAddress);
        if (currentCallback != null) {
            cancelTimeout(currentCallback);
        }
        Runnable newCallback = () -> {
            scan.removeResult(new LwdnScanResult(deviceAddress, 0, Map.of()));
            deviceLostTimeoutCallbacks.remove(deviceAddress);
        };
        TimeoutRecord timeoutRec = new TimeoutRecord(newCallback, SystemClock.elapsedRealtime(), timeout);
        deviceLostTimeoutCallbacks.put(deviceAddress, timeoutRec);
        startTimeout(timeoutRec);
    }

    public void changePendingTimeouts(Duration newDuration) {
        if (newDuration == null) {
            cancelPendingTimeouts();
            return;
        }
        long newDurationMillis = newDuration.toMillis();
        long time = SystemClock.elapsedRealtime();
        for (var timeout : deviceLostTimeoutCallbacks.entrySet()) {
            cancelTimeout(timeout.getValue());
            float elapsedRatio = (time - timeout.getValue().startedAt) / (float) newDuration.toMillis();
            if (elapsedRatio > 1f) {
                elapsedRatio = 1f;
            }
            long remainingMillis = (long) (newDurationMillis * (1f - elapsedRatio));
            TimeoutRecord oldTimeout = timeout.getValue();
            TimeoutRecord newTimeout = new TimeoutRecord(oldTimeout.callback(), time, Duration.ofMillis(remainingMillis));
            timeout.setValue(newTimeout);
            startTimeout(newTimeout);
        }
    }

    public void cancelPendingTimeouts() {
        for (TimeoutRecord callback : deviceLostTimeoutCallbacks.values()) {
            cancelTimeout(callback);
        }
        deviceLostTimeoutCallbacks.clear();
    }

    private void startTimeout(TimeoutRecord timeout) {
        handler.postDelayed(timeout.callback(), timeout.timeout.toMillis());
    }

    private void cancelTimeout(TimeoutRecord timeout) {
        handler.removeCallbacks(timeout.callback());
    }

    private static record TimeoutRecord(Runnable callback, long startedAt, Duration timeout) {

    }
}
