package cz.spojenka.lwdn;

import java.util.List;
import java.util.UUID;

public interface LwdnScanner {

    public boolean isAvailable();
    public boolean isUsingExtendedAdvertising();
    public LwdnScan startScan(List<LwdnServiceID> services, LwdnScanConfig config);
}
