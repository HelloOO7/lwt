package cz.spojenka.lwdn;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import androidx.annotation.Nullable;
import cz.spojenka.lwdn.util.DeviceSpecifics;

public class BluetoothLwdnScanner implements LwdnScanner {

    private final Context context;
    private final BluetoothAdapter adapter;
    private final int addressPsm;

    private final Handler handler;

    public BluetoothLwdnScanner(Context context, BluetoothAdapter adapter, int addressPsm) {
        this.context = context;
        this.adapter = adapter;
        this.addressPsm = addressPsm;
        handler = new Handler(Looper.getMainLooper());
    }

    public static @Nullable BluetoothLwdnScanner create(Context context, int addressPsm) {
        BluetoothAdapter adapter = BluetoothLeScan.getBluetoothAdapter(context);
        if (adapter != null) {
            return new BluetoothLwdnScanner(context, adapter, addressPsm);
        }
        return null;
    }

    public static boolean isSupported(Context context) {
        return BluetoothLeScan.isSupported(context);
    }

    public static boolean isBackgroundLocationPermissionNeeded(Context context) {
        return BluetoothLeScan.isBackgroundLocationPermissionNeeded(context);
    }

    public boolean canStartInBackground() {
        return BluetoothLeScan.canStartInBackground(context);
    }

    @Override
    public boolean isAvailable() {
        return adapter.isEnabled();
    }

    @Override
    public LwdnScan startScan(List<LwdnServiceID> services, LwdnScanConfig config) {
        LwdnScan scan = new LwdnScan();

        new ScanController(scan).startScan(services, config);

        return scan;
    }

    private class ScanController {

        private BluetoothLeScan leScan;
        private final LwdnScan scan;

        private DeviceLostTimeoutManager lostTimeoutManager;

        public ScanController(LwdnScan scan) {
            this.scan = scan;
            this.lostTimeoutManager = new DeviceLostTimeoutManager(handler, scan);
            scan.setCancellationHandler(this::stopScan);
        }

        public void startScan(List<LwdnServiceID> services, LwdnScanConfig config) {
            // if extended adv is used, only extended services are scanned, otherwise, only legacy services
            List<LwdnServiceID.BluetoothUUID> btUuids = getRelevantServiceUUIDs(services);
            boolean isUsingExtendedAdvertising = containsExtendedService(btUuids);

            Predicate<ScanResult> softwareFilter = buildSoftwareScanFilterIfNeeded(btUuids);

            ScanSettings settings = buildScanSettings(config, isUsingExtendedAdvertising);
            List<ScanFilter> filters = buildScanFilters(services);

            leScan = new BluetoothLeScan.Builder(context)
                    .setSettings(settings)
                    .setFilters(filters)
                    .setScanTimeout(config.getTimeout())
                    .build();

            leScan.addOnResultListener(new BluetoothLeScan.OnResultListener() {
                @Override
                public void onResult(BluetoothLeScan leScan, ScanResult result) {
                    if (isUsingExtendedAdvertising && result.isLegacy()) {
                        // devices using software filtering (device name only at first) will receive
                        // both legacy and extended results, which we do not want in LWDN (extended
                        // advertising has its own service ID)
                        return;
                    }
                    if (!softwareFilter.test(result)) {
                        return;
                    }
                    Log.d("BluetoothLwdnScanner", "onScanResult: " + result.getDevice().getAddress() + " rssi=" + result.getRssi());
                    if (scan.isFinished()) {
                        // result after timeout
                        return;
                    }

                    if (scan.getResultCount() < config.getMaxDevices()) {
                        BluetoothLwdnAddress address = lwdnAddressFromResult(result);
                        if (result.getScanRecord() != null) {
                            Map<LwdnServiceID, byte[]> serviceData = new HashMap<>();
                            for (LwdnServiceID.BluetoothUUID btUuid : btUuids) {
                                byte[] data = result.getScanRecord().getServiceData().get(new ParcelUuid(btUuid.uuid()));
                                if (data != null) {
                                    serviceData.put(btUuid, data);
                                }
                            }
                            scan.addResult(new LwdnScanResult(address, result.getRssi(), serviceData));
                        }
                        lostTimeoutManager.updateDeviceLostTimeout(address, config.getDeviceLostTimeout());
                    }
                    if (scan.getResultCount() >= config.getMaxDevices()) {
                        stopScan();
                    }
                }

                @Override
                public void onResultLost(BluetoothLeScan leScan, ScanResult result) {
                    scan.removeResult(new LwdnScanResult(lwdnAddressFromResult(result), result.getRssi(), Map.of()));
                }

                private BluetoothLwdnAddress lwdnAddressFromResult(ScanResult result) {
                    return new BluetoothLwdnAddress(result.getDevice(), addressPsm);
                }

                @Override
                public void onFailure(BluetoothLeScan leScan, Exception e) {
                    // handled in OnFinishedListener
                }
            });

            leScan.addOnFinishedListener(new BluetoothLeScan.OnFinishedListener() {
                @Override
                public void onFinished(BluetoothLeScan leScan) {
                    scan.markFinished();
                }

                @Override
                public void onFinishedExceptionally(BluetoothLeScan leScan, Exception e) {
                    if (e instanceof SecurityException) {
                        scan.markFailed(new LwdnScanException(ScanErrorCode.NOT_PERMITTED, e));
                    } else if (e instanceof UnsupportedOperationException) {
                        scan.markFailed(new LwdnScanException(ScanErrorCode.NOT_SUPPORTED, e));
                    } else if (e instanceof BluetoothLeScanException leScanException) {
                        scan.markFailed(new LwdnScanException(switch (leScanException.getErrorCode()) {
                            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
                                    ScanErrorCode.NOT_SUPPORTED;
                            case ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
                                    ScanErrorCode.ALREADY_RUNNING;
                            case BluetoothLeScannerCompat.SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
                                    ScanErrorCode.THROTTLED;
                            case ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
                                    ScanErrorCode.OUT_OF_RESOURCES;
                            default -> ScanErrorCode.INTERNAL_ERROR;
                        }));
                    }
                }
            });

            leScan.start();
        }

        private boolean containsExtendedService(List<? extends LwdnServiceID> services) {
            for (LwdnServiceID serviceID : services) {
                if (serviceID instanceof LwdnServiceID.BluetoothUUID bt && bt.isExtended()) {
                    return true;
                }
            }
            return false;
        }

        private List<LwdnServiceID.BluetoothUUID> getRelevantServiceUUIDs(List<LwdnServiceID> services) {
            // this must be evaluated here, because the API returns false if bluetooth is off
            boolean canUseExtended = adapter.isLeExtendedAdvertisingSupported();
            boolean hasAnyExtended = containsExtendedService(services);
            List<LwdnServiceID.BluetoothUUID> out = new ArrayList<>();
            for (LwdnServiceID serviceID : services) {
                if (serviceID instanceof LwdnServiceID.BluetoothUUID bt) {
                    if (canUseExtended && hasAnyExtended) {
                        if (bt.isExtended()) {
                            out.add(bt);
                        }
                    } else {
                        if (!bt.isExtended()) {
                            out.add(bt);
                        }
                    }
                }
            }

            return out;
        }

        private ScanSettings buildScanSettings(LwdnScanConfig config, boolean isUsingExtendedAdvertising) {
            ScanSettings.Builder settings = new ScanSettings.Builder()
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setScanMode(
                            config.getScanMode() == LwdnScanConfig.ScanMode.LOW_LATENCY
                                    ? ScanSettings.SCAN_MODE_LOW_LATENCY
                                    : ScanSettings.SCAN_MODE_LOW_POWER
                    )
                    .setLegacy(!isUsingExtendedAdvertising);

            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                settings.setScanType(ScanSettings.SCAN_TYPE_PASSIVE);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                settings.setRssiThreshold(config.getMinRssi());
            }
            return settings.build();
        }

        private List<ScanFilter> buildScanFilters(List<LwdnServiceID> services) {
            List<ScanFilter> filters = new ArrayList<>();
            for (LwdnServiceID serviceId : services) {
                if (!BluetoothLeScan.shouldUseSoftwareUUIDFiltering(context)) {
                    if (serviceId instanceof LwdnServiceID.BluetoothUUID serviceBluetoothUUID) {
                        filters.add(
                                new ScanFilter.Builder()
                                        // data+mask is needed (even though frontend allows a null value), because otherwise
                                        // the filter is ignored further down the BT stack
                                        .setServiceData(new ParcelUuid(serviceBluetoothUUID.uuid()), new byte[0], new byte[0])
                                        .build()
                        );
                    }
                } else {
                    if (serviceId instanceof LwdnServiceID.DeviceName deviceName) {
                        filters.add(
                                new ScanFilter.Builder()
                                        .setDeviceName(deviceName.name())
                                        .build()
                        );
                    }
                }
            }
            if (filters.isEmpty()) {
                throw new IllegalArgumentException("No compatible service IDs were provided for bluetooth scan, must give at least 1 UUID-type service ID.");
            }
            return filters;
        }

        private Predicate<ScanResult> buildSoftwareScanFilterIfNeeded(List<LwdnServiceID.BluetoothUUID> services) {
            if (!BluetoothLeScan.shouldUseSoftwareUUIDFiltering(context)) {
                return result -> true;
            } else {
                return buildSoftwareScanFilter(services);
            }
        }

        private static Predicate<ScanResult> buildSoftwareScanFilter(List<LwdnServiceID.BluetoothUUID> services) {
            Set<ParcelUuid> serviceUUIDs = new HashSet<>();
            for (LwdnServiceID.BluetoothUUID serviceId : services) {
                serviceUUIDs.add(new ParcelUuid(serviceId.uuid()));
            }
            if (serviceUUIDs.isEmpty()) {
                return result -> true;
            }
            return result -> {
                if (result.getScanRecord() != null) {
                    for (var service : result.getScanRecord().getServiceData().keySet()) {
                        if (serviceUUIDs.contains(service)) {
                            return true;
                        }
                    }
                }
                return false;
            };
        }

        public void stopScan() {
            if (leScan != null) {
                leScan.stop();
            }
            lostTimeoutManager.cancelPendingTimeouts();
            scan.markFinished();
        }
    }
}
