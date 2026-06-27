package cz.spojenka.lwdn;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;

import java.util.List;
import java.util.UUID;

import androidx.annotation.RequiresPermission;

public class HybridLwdnScanner implements LwdnScanner {

    private BluetoothLwdnScanner bluetoothScanner;

    public void addBluetoothScanner(BluetoothAdapter bluetoothAdapter, int addressPsm) {
        this.bluetoothScanner = new BluetoothLwdnScanner(bluetoothAdapter, addressPsm);
    }

    public boolean isBluetoothScannerAvailable() {
        return bluetoothScanner != null && bluetoothScanner.isAvailable();
    }

    public boolean isUsingExtendedAdvertising() {
        return isWifiAwareScannerAvailable() || (isBluetoothScannerAvailable() && bluetoothScanner.isUsingExtendedAdvertising());
    }

    public boolean isWifiAwareScannerAvailable() {
        // placeholder
        return false;
    }

    @Override
    public boolean isAvailable() {
        return isBluetoothScannerAvailable();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    @Override
    public LwdnScan startScan(List<UUID> services, LwdnScanConfig config) {
        if (isBluetoothScannerAvailable()) {
            return bluetoothScanner.startScan(services, config);
        }
        LwdnScan failedScan = new LwdnScan();
        failedScan.markFailed(new LwdnScanException(ScanErrorCode.NOT_SUPPORTED, "No available scanner found"));
        return failedScan;
    }
}
