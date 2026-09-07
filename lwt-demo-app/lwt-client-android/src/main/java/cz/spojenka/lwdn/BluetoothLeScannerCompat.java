package cz.spojenka.lwdn;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;

import java.util.List;

import androidx.annotation.RequiresPermission;

public class BluetoothLeScannerCompat {

    // the ScanCallback constant {@link ScanCallback#SCAN_FAILED_SCANNING_TOO_FREQUENTLY} is not available in Android 12 and below, so we define it here
    public static final int SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6;

    @SuppressLint("WrongConstant")
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public static void startScan(Context context, BluetoothLeScanner scanner, List<ScanFilter> filters, ScanSettings settings, ScanCallback callback) {
        BluetoothLeThrottling.tryStartRateLimitedScan(
                context,
                () -> scanner.startScan(filters, settings, callback),
                () -> callback.onScanFailed(SCAN_FAILED_SCANNING_TOO_FREQUENTLY)
        );
    }
}
