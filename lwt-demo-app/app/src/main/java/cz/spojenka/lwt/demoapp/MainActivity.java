package cz.spojenka.lwt.demoapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.time.Duration;
import java.util.List;

import javax.net.ssl.SSLContext;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.lwdn.LwdnScanConfig;
import cz.spojenka.lwdn.LwdnScanException;
import cz.spojenka.lwt.*;
import cz.spojenka.lwt.demoapp.databinding.ActivityMainBinding;
import cz.spojenka.lwt.util.LwtTime;
import cz.spojenka.lwtp.LwtpTLSConfig;
import cz.spojenka.lwtp.LwtpTLSPolicy;

public class MainActivity extends BaseActivity {

    private static final String TAG = "LWTDemoApp";

    private LwtDeviceScanner lwtScanner;

    private SSLContext sslContext;

    private ActivityMainBinding binding;

    private LwtDevice foundDevice;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setButtonsEnabled(false);
        sslContext = createSSLContext();
        lwtScanner = new LwtDeviceScanner(this);
        if (hasBluetoothScanPermission()) {
            lwtScanner.startScan(
                    new LwdnScanConfig.Builder()
                            .setMaxDevices(1)
                            .setTimeout(Duration.ofSeconds(5))
                            .build()
            ).addOnResultListener(new LwtScan.OnResultListener() {
                @Override
                public void onResult(LwtScan scan, LwtDevice result) {
                    Log.i(TAG, "Found device: " + result);
                    foundDevice = result;
                    setButtonsEnabled(true);
                }

                @Override
                public void onFailure(LwtScan scan, LwdnScanException e) {
                    Log.e(TAG, "Scan failed", e);
                }
            });
        } else {
            Toast.makeText(this, "Bluetooth scan permission not granted.", Toast.LENGTH_LONG).show();
        }
        binding.btnTest.setOnClickListener(v -> checkTrustAndRunTest());
        binding.btnTestTls.setOnClickListener(v -> runTestOverTLS());
        testWifiAware();

        binding.btnShowDeviceList.setOnClickListener(v -> startActivity(new Intent(this, DeviceListActivity.class)));
        binding.btnRunTicketActivation.setOnClickListener(v -> {
            int numZones;
            int validityMinutes;
            String[] prepaidZones;
            try {
                numZones = Integer.parseInt(binding.etNumZones.getText().toString());
                validityMinutes = Integer.parseInt(binding.etValidMinutes.getText().toString());
                prepaidZones = binding.etPrepaidZones.getText().toString().replace(" ", "").split(",");
            } catch (NumberFormatException ex) {
                return;
            }

            startActivity(
                    new Intent(this, TicketActivationActivity.class)
                            .putExtra(
                                    TicketActivationActivity.EXTRA_TICKET,
                                    new TicketData(
                                            "PID",
                                            numZones,
                                            List.of("P", "0", "B", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13"),
                                            Duration.ofMinutes(validityMinutes),
                                            TestingTicketData.SIGNED_ACTIVATION_TOKEN
                                    )
                            )
                            .putExtra(
                                    TicketActivationActivity.EXTRA_PREPAID_ZONES,
                                    prepaidZones
                            )
            );
        });
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
        LwtAPIClient client = new LwtAPIClient(foundDevice.getAddress());
        client.setSocketWatchdogTimeout(Duration.ofSeconds(5));
        client.disableTLS();
        client.authenticateServer(GlobalTrustManager.getInstance(getApplication()), CommType.ENQUEUE).whenCompleteAsync((trusted, error) -> {
            if (error != null) {
                Log.e(TAG, "Server auth operation error", error);
                setButtonsEnabled(true);
            } else {
                Log.i(TAG, "Server authentication result: " + trusted);
            }
        }, getMainExecutor());
        enqueueTestOperations(client);
        executeOps(client);
    }

    private void runTestOverTLS() {
        initTest();
        LwtAPIClient client = new LwtAPIClient(foundDevice.getAddress());
        client.setSocketWatchdogTimeout(Duration.ofSeconds(5));
        client.useTLS(
                new LwtpTLSConfig.Builder(foundDevice.getAddress())
                        .setTLSPolicy(LwtpTLSPolicy.EXPLICIT_OPPORTUNISTIC)
                        .setSSLContext(sslContext)
                        .build()
        );
        enqueueTestOperations(client);
        executeOps(client);
    }

    private void initTest() {
        setButtonsEnabled(false);
        Log.i(TAG, "Testing bluetooth communication with device: " + foundDevice.getAddress());
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
        client.getTripRouteInfo(CommType.ENQUEUE).thenAccept(tripRouteInfo -> {
            Log.i(TAG, "Trip route info received: " + routeInfoToString(tripRouteInfo));
        }).exceptionally(ex -> {
            Log.e(TAG, "LWT operation failed", ex);
            return null;
        });
        client.getTicketValidationInfo(CommType.ENQUEUE).thenAccept(tvi -> {
            Log.i(TAG, "Ticket validation info received: zone " + tvi.tariffZones() + ", act. time=" + LwtTime.convertLocalDateTime(tvi.scheduledActivationTime()));
        }).exceptionally(ex -> {
            Log.e(TAG, "LWT operation failed", ex);
            return null;
        });
    }

    private String routeInfoToString(TripRouteInfo t) {
        return t.trip().trip().line().name() + " (" + t.trip().trip().globalRefId() + ") "
                + t.stopsLength() + " stops";
    }

    private void executeOps(LwtAPIClient client) {
        client.executeAsync().whenCompleteAsync((unused, throwable) -> {
            setButtonsEnabled(true);
        }, getMainExecutor());
    }

    private void setButtonsEnabled(boolean enabled) {
        binding.btnTest.setEnabled(enabled);
        binding.btnTestTls.setEnabled(enabled);
    }

    private SSLContext createSSLContext() {
        return GlobalTrustManager.createSSLContext(getApplication());
    }
}
