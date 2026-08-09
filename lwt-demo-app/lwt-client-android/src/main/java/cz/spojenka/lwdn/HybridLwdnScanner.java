package cz.spojenka.lwdn;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.aware.WifiAwareManager;

import java.util.List;

public class HybridLwdnScanner implements LwdnScanner {

    private BluetoothLwdnScanner bluetoothScanner;
    private WifiAwareLwdnScanner wifiAwareScanner;

    public void addBluetoothScanner(Context context, BluetoothAdapter bluetoothAdapter, int addressPsm) {
        this.bluetoothScanner = new BluetoothLwdnScanner(context, bluetoothAdapter, addressPsm);
    }

    public void addWifiAwareScanner(WifiAwareManager awareManager, WifiAwareSessionManager sessionManager, int addressPort) {
        this.wifiAwareScanner = new WifiAwareLwdnScanner(awareManager, sessionManager, addressPort);
    }

    public boolean isBluetoothScannerAvailable() {
        return bluetoothScanner != null && bluetoothScanner.isAvailable();
    }

    public boolean isUsingExtendedAdvertising() {
        return isWifiAwareScannerAvailable() || (isBluetoothScannerAvailable() && bluetoothScanner.isUsingExtendedAdvertising());
    }

    public boolean isWifiAwareScannerAvailable() {
        return wifiAwareScanner != null && wifiAwareScanner.isAvailable();
    }

    @Override
    public boolean isAvailable() {
        return isBluetoothScannerAvailable() || isWifiAwareScannerAvailable();
    }

    @Override
    public LwdnScan startScan(List<LwdnServiceID> services, LwdnScanConfig config) {
        if (isWifiAwareScannerAvailable()) {
            return wifiAwareScanner.startScan(services, config);
        }
        if (isBluetoothScannerAvailable()) {
            return bluetoothScanner.startScan(services, config);
        }
        LwdnScan failedScan = new LwdnScan();
        failedScan.markFailed(new LwdnScanException(ScanErrorCode.NOT_SUPPORTED, "No available scanner found"));
        return failedScan;
    }
}
