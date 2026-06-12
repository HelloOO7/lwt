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
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.SSLContext;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwt.*;
import cz.spojenka.lwt.util.BLEScanRecordUtil;
import cz.spojenka.lwt.util.TLSTrustManager;
import cz.spojenka.lwtp.LwtpTLSConfig;
import cz.spojenka.lwtp.LwtpTLSPolicy;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "LWTDemoApp";

    private BluetoothAdapter bluetoothAdapter;
    private TLSTrustManager trustManager;
    private SSLContext sslContext;

    private Button btnRunTest;
    private Button btnRunTlsTest;

    private LwdnAddress foundDevAddress;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnRunTest = findViewById(R.id.btnTest);
        btnRunTlsTest = findViewById(R.id.btnTestTls);
        setButtonsEnabled(false);
        sslContext = createSSLContext();
        bluetoothAdapter = getSystemService(BluetoothManager.class).getAdapter();
        ScanFilter filter = new ScanFilter.Builder()
                // mask is needed otherwise the filter does not work properly for our 32bit UUID.
                // furthermore, the service UUID filter MUST be used instead of service data filter (which would be more space efficient),
                // as the service data filter just plain does not work on some devices (is completely ignored, most likely due to HW offloading)
                .setDeviceName("LWT")
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        if (hasBluetoothScanPermission()) {
            bluetoothAdapter.getBluetoothLeScanner().startScan(List.of(filter), settings, scanCallback);
        } else {
            Toast.makeText(this, "Bluetooth scan permission not granted.", Toast.LENGTH_LONG).show();
        }
        btnRunTest.setOnClickListener(v -> checkTrustAndRunTest());
        btnRunTlsTest.setOnClickListener(v -> runTestOverTLS());
        testWifiAware();
    }

    private UUID make32BitUUID(int value) {
        return new UUID(Integer.toUnsignedLong(value) << 32, 0);
    }

    private boolean hasBluetoothScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void testWifiAware() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            return;
        }
        WifiAwareManager awareManager = getSystemService(WifiAwareManager.class);
        awareManager.attach(new AttachCallback() {
            @Override
            public void onAttached(WifiAwareSession session) {
                Log.i(TAG, "Wi-Fi Aware attached: " + session);
            }

            @Override
            public void onAttachFailed() {
                Log.e(TAG, "Wi-Fi Aware attach failed");
            }

            @Override
            public void onAwareSessionTerminated() {
                Log.w(TAG, "Wi-Fi Aware session terminated");
            }
        }, new Handler(getMainLooper()));
    }

    private void checkTrustAndRunTest() {
        initTest();
        LwtAPIClient client = new LwtAPIClient(foundDevAddress);
        client.setSocketWatchdogTimeout(Duration.ofSeconds(5));
        client.disableTLS();
        client.authenticateServer(trustManager, CommType.ENQUEUE).whenCompleteAsync((trusted, error) -> {
            if (error != null) {
                Log.e(TAG, "Server auth operation error", error);
                btnRunTest.setEnabled(true);
            } else {
                Log.i(TAG, "Server authentication result: " + trusted);
            }
        }, getMainExecutor());
        enqueueTestOperations(client);
        executeOps(client);
    }

    private void runTestOverTLS() {
        initTest();
        LwtAPIClient client = new LwtAPIClient(foundDevAddress);
        client.setSocketWatchdogTimeout(Duration.ofSeconds(5));
        client.useTLS(
                new LwtpTLSConfig.Builder(foundDevAddress)
                        .setTLSPolicy(LwtpTLSPolicy.EXPLICIT_OPPORTUNISTIC)
                        .setSSLContext(sslContext)
                        .build()
        );
        enqueueTestOperations(client);
        executeOps(client);
    }

    private void initTest() {
        setButtonsEnabled(false);
        Log.i(TAG, "Testing bluetooth communication with device: " + foundDevAddress);
    }

    private void enqueueTestOperations(LwtAPIClient client) {
        for (int i = 0; i < 1; i++) {
            client.ping(CommType.ENQUEUE).thenAccept(pingResponse -> {
                Log.i(TAG, "Ping response received: dev=" + pingResponse.deviceId() + ", time=" + pingResponse.deviceTime());
            }).exceptionally(ex -> {
                Log.e(TAG, "LWT operation failed", ex);
                return null;
            });
        }
    }

    private void executeOps(LwtAPIClient client) {
        client.executeAsync().whenCompleteAsync((unused, throwable) -> {
            setButtonsEnabled(true);
        }, getMainExecutor());
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i(TAG, "Found device: " + result.getDevice());
            if (result.getScanRecord() != null) {
                byte[] data = BLEScanRecordUtil.getField(result.getScanRecord(), 32);
                if (data != null && BLEScanRecordUtil.getServiceUUID32(data) == 0x4C575456) {
                    try {
                        TripAdvertisementDataLegacy advData = TripAdvertisementDataLegacy.unwrap(BLEScanRecordUtil.getServiceDataPayload(data, Integer.BYTES));
                        Log.i(TAG, advData.toString());
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to parse advertisement data", e);
                    }
                }
            }
            setButtonsEnabled(true);
            foundDevAddress = LwtAPIClient.bluetoothAddress(result.getDevice());
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
        }
    };

    private void setButtonsEnabled(boolean enabled) {
        btnRunTest.setEnabled(enabled);
        btnRunTlsTest.setEnabled(enabled);
    }

    private SSLContext createSSLContext() {
        try {
            trustManager = new TLSTrustManager();
            trustManager.addCertificate(getAssets(), "ROPID_Root_CA_Certificate_[DEBUG].crt", "Root CA");
            return trustManager.createSSLContext();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
