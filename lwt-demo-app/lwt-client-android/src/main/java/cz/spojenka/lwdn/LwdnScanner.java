package cz.spojenka.lwdn;

import java.util.List;
import java.util.UUID;

public interface LwdnScanner {

    public boolean isAvailable();
    public LwdnScan startScan(List<UUID> services, LwdnScanConfig config);
}
