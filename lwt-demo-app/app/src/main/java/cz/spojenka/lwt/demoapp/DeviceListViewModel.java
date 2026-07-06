package cz.spojenka.lwt.demoapp;

import android.app.Application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import cz.spojenka.android.system.livedata.LiveList;
import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwdn.LwdnScanException;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.LwtDeviceType;
import cz.spojenka.lwt.LwtScan;

public class DeviceListViewModel extends AndroidViewModel implements LwtScan.OnResultListener, LwtScan.OnFinishedListener {

    private List<LwtDeviceType> deviceTypes;
    private boolean useContinuousScan = false;
    private LwtScan currentScan;

    private final LiveList<LwtDevice> deviceResults = new LiveList<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(true);
    private final MutableLiveData<LwdnScanException> scanError = new MutableLiveData<>();

    private boolean showInactiveDevies = false;
    private final List<LwdnAddress> hiddenAddresses = new ArrayList<>();

    public DeviceListViewModel(@NonNull Application application) {
        super(application);
        addCloseable(this::close);
    }

    public void close() {
        if (useContinuousScan) {
            // continuous scan must not leak
            cancel();
        } else {
            // leave results for other activities
            unbindGlobalObservers();
        }
    }

    public void setDeviceTypes(List<LwtDeviceType> deviceTypes) {
        this.deviceTypes = deviceTypes;
    }

    public void setDeviceTypes(LwtDeviceType[] deviceTypes) {
        if (deviceTypes != null && deviceTypes.length > 0) {
            this.deviceTypes = List.of(deviceTypes);
        } else {
            this.deviceTypes = null;
        }
    }

    public void setUseContinuousScan(boolean useContinuousScan) {
        this.useContinuousScan = useContinuousScan;
    }

    public void setShowInactiveDevies(boolean showInactiveDevies) {
        this.showInactiveDevies = showInactiveDevies;
    }

    public void hideDevicesWithAddress(LwdnAddress address) {
        if (!hiddenAddresses.contains(address)) {
            hiddenAddresses.add(address);

            for (int i = 0; i < deviceResults.size(); i++) {
                if (deviceResults.get(i).getAddress().equals(address)) {
                    deviceResults.remove(i);
                    break;
                }
            }
        }
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
        unbindGlobalObservers();
        if (currentScan != null) {
            currentScan.cancel();
            currentScan = null;
        }
    }

    private void unbindGlobalObservers() {
        if (currentScan != null) {
            currentScan.removeOnFinishedListener(this);
            currentScan.removeOnResultListener(this);
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

    private boolean canKeepDeviceAtIndex(LwtDevice device, int index) {
        LwtDevice leftDev = index > 0 ? deviceResults.get(index - 1) : null;
        LwtDevice rightDev = index < deviceResults.size() - 1 ? deviceResults.get(index + 1) : null;
        int rssi = device.getScanResult().rssi();
        return (leftDev == null || leftDev.getScanResult().rssi() >= rssi) && (rightDev == null || rightDev.getScanResult().rssi() <= rssi);
    }

    private boolean isDeviceHidden(LwtDevice device) {
        return hiddenAddresses.contains(device.getAddress());
    }

    private boolean isDeviceInactive(LwtDevice device) {
        if (device instanceof LwtDevice.Vehicle v) {
            return !v.getAdvData().isEnRoute();
        }
        return false;
    }

    private void insertResult(LwtDevice device) {
        if (isDeviceHidden(device) || (!showInactiveDevies && isDeviceInactive(device))) {
            return;
        }

        int existingIndex = getDeviceIndexByAddress(device);
        if (existingIndex != -1) {
            if (canKeepDeviceAtIndex(device, existingIndex)) {
                deviceResults.set(existingIndex, device);
                return;
            } else {
                // move and add again - LiveList does not support swapping
                deviceResults.remove(existingIndex);
            }
        }
        int insertIndex = Collections.binarySearch(deviceResults.asList(), device, Comparator.comparingInt((LwtDevice dev) -> dev.getScanResult().rssi()).reversed());
        if (insertIndex < 0) {
            insertIndex = -insertIndex - 1;
        }
        deviceResults.add(insertIndex, device);
    }

    public void startScan() {
        load();
    }

    public void reloadIfNotLoading() {
        if (!isLoading()) {
            reload();
        }
    }

    public void reload() {
        cancel();
        // if scan was initiated externally or from a previous activity, local reference will be null,
        // so cancel it here globally to ensure full reload.
        GlobalLwtScanner.getInstance(getApplication()).cancelScan(deviceTypes);
        load();
    }

    private void load() {
        unbindGlobalObservers();
        deviceResults.clear();

        isLoading.setValue(true);

        currentScan = GlobalLwtScanner.getInstance(getApplication()).scan(deviceTypes, useContinuousScan);
        currentScan.addOnResultListener(this);
        currentScan.addOnFinishedListener(this);
    }

    public LiveData<LwdnScanException> getScanError() {
        return scanError;
    }

    public void ackScanError() {
        scanError.setValue(null);
    }

    @Override
    public void onResult(LwtScan scan, LwtDevice result) {
        insertResult(result);
    }

    @Override
    public void onFailure(LwtScan scan, LwdnScanException e) {
        // ignore - handle in onFinishedExceptionally
    }

    @Override
    public void onFinished(LwtScan scan) {
        isLoading.setValue(false);
    }

    @Override
    public void onFinishedExceptionally(LwtScan scan, LwdnScanException e) {
        isLoading.setValue(false);
        scanError.setValue(e);
    }
}
