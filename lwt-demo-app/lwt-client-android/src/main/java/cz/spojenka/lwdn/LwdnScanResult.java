package cz.spojenka.lwdn;

import java.util.Map;
import java.util.UUID;

public record LwdnScanResult(
        LwdnAddress deviceAddress,
        Map<UUID, byte[]> serviceData
) {
}
