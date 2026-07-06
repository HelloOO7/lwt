package cz.spojenka.lwdn;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import java.util.List;
import java.util.UUID;

public class HybridLwdnScanner implements LwdnScanner {

    private BluetoothLwdnScanner bluetoothScanner;

    public void addBluetoothScanner(Context context, BluetoothAdapter bluetoothAdapter, int addressPsm) {
        this.bluetoothScanner = new BluetoothLwdnScanner(context, bluetoothAdapter, addressPsm);
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
