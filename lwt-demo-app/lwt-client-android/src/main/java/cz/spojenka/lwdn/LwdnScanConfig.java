package cz.spojenka.lwdn;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public class LwdnScanConfig {

    private final Duration timeout;
    private final int maxDevices;
    private final int minRssi;
    private final int maxDistanceMm;

    private LwdnScanConfig(Duration timeout, int maxDevices, int minRssi, int maxDistanceMm) {
        this.timeout = timeout;
        this.maxDevices = maxDevices;
        this.minRssi = minRssi;
        this.maxDistanceMm = maxDistanceMm;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxDevices() {
        return maxDevices;
    }

    public int getMinRssi() {
        return minRssi;
    }

    public int getMaxDistanceMm() {
        return maxDistanceMm;
    }

    public boolean hasMaxDistance() {
        return maxDistanceMm != Integer.MAX_VALUE;
    }

    public static class Builder {

        private Duration timeout = Duration.ofSeconds(10);
        private int maxDevices = Integer.MAX_VALUE;
        private int minRssi = -127;
        private int maxDistanceMm = Integer.MAX_VALUE;

        public Builder setTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder setMaxDevices(int maxDevices) {
            this.maxDevices = maxDevices;
            return this;
        }

        public Builder setMinRssi(int minRssi) {
            this.minRssi = minRssi;
            return this;
        }

        public void setMaxDistanceMm(int maxDistanceMm) {
            this.maxDistanceMm = maxDistanceMm;
        }

        public LwdnScanConfig build() {
            return new LwdnScanConfig(timeout, maxDevices, minRssi, maxDistanceMm);
        }
    }
}
