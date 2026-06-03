package cz.spojenka.lwt.demoapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.flatbuffers.FlatBufferBuilder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import cz.spojenka.lwdn.BluetoothLwdnSocketFactory;
import cz.spojenka.lwt.*;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LWTDemoApp";

    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) && getSystemService(WifiAwareManager.class).isAvailable()) {
            Toast.makeText(this, "Wi-Fi Aware is supported and available on this device.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Wi-Fi Aware is not supported or not available on this device.", Toast.LENGTH_LONG).show();
        }*/
        bluetoothAdapter = getSystemService(BluetoothManager.class).getAdapter();
        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceName("LWT ESP32")
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.getBluetoothLeScanner().startScan(List.of(filter), settings, scanCallback);
        } else {
            Toast.makeText(this, "Bluetooth scan permission not granted.", Toast.LENGTH_LONG).show();
        }
    }

    private void testBluetoothComm(BluetoothDevice dev) {
        LwtAPIClient client = new LwtAPIClient(LwtAPIClient.bluetoothSocketFactory(dev));
        for (int i = 0; i < 4; i++) {
            client.enqueue(LwtAPI::ping).thenAccept(pingResponse -> {
                Log.i(TAG, "Ping response received: dev=" + pingResponse.deviceId() + ", time=" + pingResponse.deviceTime());
            }).exceptionally(ex -> {
                Log.e(TAG, "LWT operation failed", ex);
                return null;
            });
        }
        client.execute();
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            testBluetoothComm(result.getDevice());
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        }
    };
}
