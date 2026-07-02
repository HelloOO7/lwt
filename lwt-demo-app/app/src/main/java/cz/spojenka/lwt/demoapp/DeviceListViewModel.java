package cz.spojenka.lwt.demoapp;

import android.app.Application;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import cz.spojenka.android.system.livedata.LiveList;
import cz.spojenka.lwdn.LwdnScanConfig;
import cz.spojenka.lwdn.LwdnScanException;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.LwtDeviceScanner;
import cz.spojenka.lwt.LwtScan;

public class DeviceListViewModel extends AndroidViewModel {

    private final LwtDeviceScanner scanner;

    private LwtScan currentScan;

    private final LiveList<LwtDevice> deviceResults = new LiveList<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(true);
    private final MutableLiveData<LwdnScanException> scanError = new MutableLiveData<>();

    public DeviceListViewModel(@NonNull Application application) {
        super(application);
        scanner = new LwtDeviceScanner(application);
        reload();
    }

    public LiveList<LwtDevice> getDeviceResults() {
        return deviceResults;
    }

    public boolean isLoading() {
        return isLoading.getValue() != null && isLoading.getValue();
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void cancel() {
        if (currentScan != null) {
            currentScan.cancel();
            currentScan = null;
        }
    }

    private int getDeviceIndexByAddress(LwtDevice device) {
        for (int i = 0; i < deviceResults.size(); i++) {
            if (deviceResults.get(i).getAddress().equals(device.getAddress())) {
                return i;
            }
        }
        return -1;
    }

    private void insertResult(LwtDevice device) {
        int existingIndex = getDeviceIndexByAddress(device);
        if (existingIndex != -1) {
            deviceResults.set(existingIndex, device);
            return;
        }
        int insertIndex = Collections.binarySearch(deviceResults.asList(), device, Comparator.comparingInt((LwtDevice dev) -> dev.getScanResult().rssi()).reversed());
        if (insertIndex < 0) {
            insertIndex = -insertIndex - 1;
        }
        deviceResults.add(insertIndex, device);
    }

    public void reload() {
        cancel();
        deviceResults.clear();

        isLoading.setValue(true);

        currentScan = scanner.startScan(new LwdnScanConfig.Builder().setTimeout(Duration.ofSeconds(5)).build());
        currentScan.addOnResultListener(new LwtScan.OnResultListener() {
            @Override
            public void onResult(LwtScan scan, LwtDevice result) {
                insertResult(result);
            }

            @Override
            public void onFailure(LwtScan scan, LwdnScanException e) {

            }
        });
        currentScan.addOnFinishedListener(new LwtScan.OnFinishedListener() {
            @Override
            public void onFinished(LwtScan scan) {
                isLoading.setValue(false);
            }

            @Override
            public void onFailure(LwtScan scan, LwdnScanException e) {
                isLoading.setValue(false);
                scanError.setValue(e);
            }
        });
    }

    public LiveData<LwdnScanException> getScanError() {
        return scanError;
    }

    public void ackScanError() {
        scanError.setValue(null);
    }
}
