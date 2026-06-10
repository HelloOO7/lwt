package cz.spojenka.lwt.demoapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLContext;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwt.*;
import cz.spojenka.lwt.util.TLSTrustManager;
import cz.spojenka.lwtp.LwtpTLSConfig;
import cz.spojenka.lwtp.LwtpTLSPolicy;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LWTDemoApp";

    private BluetoothAdapter bluetoothAdapter;
    private SSLContext sslContext;

    private Button btnRunTest;

    private LwdnAddress foundDevAddress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnRunTest = findViewById(R.id.btnTest);
        btnRunTest.setEnabled(false);
        /*if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) && getSystemService(WifiAwareManager.class).isAvailable()) {
            Toast.makeText(this, "Wi-Fi Aware is supported and available on this device.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Wi-Fi Aware is not supported or not available on this device.", Toast.LENGTH_LONG).show();
        }*/
        sslContext = createSSLContext();
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
        btnRunTest.setOnClickListener(v -> testLwdnComm());
    }

    private void testLwdnComm() {
        btnRunTest.setEnabled(false);
        Log.i(TAG, "Testing bluetooth communication with device: " + foundDevAddress);
        LwtAPIClient client = new LwtAPIClient(foundDevAddress);
        client.setSocketWatchdogTimeout(Duration.ofSeconds(5));
        client.useTLS(
                new LwtpTLSConfig.Builder(foundDevAddress)
                        .setTLSPolicy(LwtpTLSPolicy.EXPLICIT_OPPORTUNISTIC)
                        .setSSLContext(sslContext)
                        .build()
        );
        for (int i = 0; i < 1; i++) {
            client.enqueue(LwtAPI::ping).thenAccept(pingResponse -> {
                Log.i(TAG, "Ping response received: dev=" + pingResponse.deviceId() + ", time=" + pingResponse.deviceTime());
            }).exceptionally(ex -> {
                Log.e(TAG, "LWT operation failed", ex);
                return null;
            });
        }
        CompletableFuture.runAsync(client::execute).whenCompleteAsync((unused, throwable) -> {
            btnRunTest.setEnabled(true);
        }, getMainExecutor());
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            btnRunTest.setEnabled(true);
            foundDevAddress = LwtAPIClient.bluetoothAddress(result.getDevice());
            testLwdnComm();
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        }
    };

    private SSLContext createSSLContext() {
        try {
            TLSTrustManager trustManager = new TLSTrustManager();
            trustManager.addCertificate(getAssets(), "ROPID_Root_CA_Certificate_[DEBUG].crt", "Root CA");
            return trustManager.createSSLContext();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
