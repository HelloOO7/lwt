package cz.spojenka.lwt;

import android.util.Log;

import java.io.IOException;
import java.util.UUID;

import cz.spojenka.lwdn.AbstractScan;
import cz.spojenka.lwdn.LwdnScan;
import cz.spojenka.lwdn.LwdnScanException;
import cz.spojenka.lwdn.LwdnScanResult;

public class LwtScan extends AbstractScan<LwtDevice, LwdnScanException, LwtScan> {

    private static final String TAG = "LwtScan";

    private final AbstractScan<?, ?, ?> baseScan;

    LwtScan(LwdnScan lwdnScan) {
        this.baseScan = lwdnScan;

        lwdnScan.addOnResultListener(new LwdnScan.OnResultListener() {
            @Override
            public void onResult(LwdnScan lwdnScan, LwdnScanResult result) {
                LwtDevice dev = createDeviceFromResult(result);
                if (dev != null) {
                    addResult(dev);
                }
            }

            @Override
            public void onFailure(LwdnScan lwdnScan, LwdnScanException e) {
                // ignore - handle in onFinishedListener
            }
        });
        lwdnScan.addOnFinishedListener(new LwdnScan.OnFinishedListener() {
            @Override
            public void onFinished(LwdnScan lwdnScan) {
                markFinished();
            }

            @Override
            public void onFinishedExceptionally(LwdnScan lwdnScan, LwdnScanException e) {
                markFailed(e);
            }
        });
    }

    LwtScan(LwtScan baseScan, ScanMapper mapper) {
        this.baseScan = baseScan;
        baseScan.addOnResultListener(new OnResultListener() {
            @Override
            public void onResult(LwtScan scan, LwtDevice result) {
                mapper.mapResult(scan, result, LwtScan.this);
            }

            @Override
            public void onFailure(LwtScan scan, LwdnScanException e) {
                // ignore - handle in onFinishedListener
            }
        });
        baseScan.addOnFinishedListener(new OnFinishedListener() {
            @Override
            public void onFinished(LwtScan scan) {
                mapper.mapFinished(scan, LwtScan.this);
            }

            @Override
            public void onFinishedExceptionally(LwtScan scan, LwdnScanException e) {
                mapper.mapFinishedExceptionally(scan, e, LwtScan.this);
            }
        });
    }

    public static LwtScan map(LwtScan base, ScanMapper mapper) {
        return new LwtScan(base, mapper);
    }

    private LwtDevice createDeviceFromResult(LwdnScanResult result) {
        byte[] vehicleData = getVehicleResultData(result);
        if (vehicleData != null) {
            try {
                TripAdvertisementData advData;
                if (TripAdvertisementDataExt.isPresent(vehicleData)) {
                    advData = TripAdvertisementDataExt.unwrap(vehicleData);
                } else {
                    advData = TripAdvertisementData.unwrap(vehicleData);
                }
                return new LwtDevice.Vehicle(result, advData);
            } catch (IOException e) {
                Log.e(TAG, "Failed to parse vehicle advertisement data", e);
            }
        }
        return null;
    }

    private byte[] getVehicleResultData(LwdnScanResult result) {
        byte[] data = result.serviceData().get(LwtServiceConstants.serviceNameForDeviceType(LwtDeviceType.VEHICLE));
        if (data == null) {
            data = result.serviceData().get(LwtServiceConstants.serviceExtendedUUIDForDeviceType(LwtDeviceType.VEHICLE));
        }
        return data;
    }

    @Override
    protected void onCancel() {
        baseScan.cancel();
    }

    /*
    protected methods to expose the superclass methods to the package, so that LwtDeviceScanner can call them.
     */

    protected void addResult(LwtDevice result) {
        super.addResult(result);
    }

    protected void markFinished() {
        super.markFinished();
    }

    protected void markFailed(LwdnScanException e) {
        super.markFailed(e);
    }

    public void addOnResultListener(OnResultListener listener) {
        addOnResultListenerImpl(listener);
    }

    public void addOnFinishedListener(OnFinishedListener listener) {
        addOnFinishedListenerImpl(listener);
    }

    public void removeOnResultListener(OnResultListener listener) {
        removeOnResultListenerImpl(listener);
    }

    public void removeOnFinishedListener(OnFinishedListener listener) {
        removeOnFinishedListenerImpl(listener);
    }

    public static interface OnResultListener extends AbstractScan.OnResultListener<LwtScan, LwtDevice, LwdnScanException> {

    }

    public static interface OnFinishedListener extends AbstractScan.OnFinishedListener<LwtScan, LwtDevice, LwdnScanException> {

    }

    public static abstract class ScanMapper {

        public void mapResult(LwtScan scan, LwtDevice result, LwtScan destScan) {
            destScan.addResult(result);
        }

        public void mapFinished(LwtScan scan, LwtScan destScan) {
            if (scan.wasCancelled()) {
                if (!destScan.wasCancelled()) {
                    destScan.cancel();
                }
            } else {
                destScan.markFinished();
            }
        }

        public void mapFinishedExceptionally(LwtScan scan, LwdnScanException e, LwtScan destScan) {
            destScan.markFailed(e);
        }

        protected void copyResults(LwtScan source, LwtScan dest) {
            source.getResults().forEach(dest::addResult);
        }

        protected void markFinished(LwtScan dest) {
            dest.markFinished();
        }
    }
}
