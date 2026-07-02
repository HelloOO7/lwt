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

    private final LwdnScan lwdnScan;

    LwtScan(LwdnScan lwdnScan) {
        this.lwdnScan = lwdnScan;

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
            public void onFailure(LwdnScan lwdnScan, LwdnScanException e) {
                markFailed(e);
            }
        });
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
        byte[] data = result.serviceData().get(make32BitUUID(LwtServiceConstants.BLE_SERVICE_UUID_VEHICLE));
        if (data == null) {
            data = result.serviceData().get(make32BitUUID(LwtServiceConstants.BLE_SERVICE_UUID_VEHICLE_EXTENDED));
        }
        return data;
    }

    static UUID make32BitUUID(int value) {
        // https://stackoverflow.com/questions/13964342/android-how-do-bluetooth-uuids-work
        return new UUID((Integer.toUnsignedLong(value) << 32) | 0x1000, 0x800000805f9b34fbL);
    }

    @Override
    protected void onCancel() {
        lwdnScan.cancel();
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
}
