package cz.spojenka.lwt.demoapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkInfo;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.DataInputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.net.ssl.SSLContext;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.android.util.ViewUtils;
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(ViewUtils.wrapInScrollView(binding.getRoot()));
        setButtonsEnabled(false);
        sslContext = createSSLContext();
        lwtScanner = new LwtDeviceScanner(this, GlobalLwtScanner.getInstance(getApplication()).getLinkSession());
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

        binding.btnRunTicketInspection.setOnClickListener(v -> startActivity(new Intent(this, TicketInspectionHomeActivity.class)));

        binding.btnSetClientCert.setOnClickListener(v -> startActivity(
                new Intent(this, ClientCertImportActivity.class)
                        .putExtra(ClientCertImportActivity.EXTRA_TARGET_ALIAS, GlobalTrustManager.APP_CLIENT_KEY_ALIAS)
        ));

        Log.d(TAG, "Client key present: " + GlobalTrustManager.isClientKeyPresent());
    }

    private void testNanDatapath() {
        getSystemService(WifiAwareManager.class).attach(new AttachCallback() {

            @Override
            public void onAttached(WifiAwareSession session) {
                /*var netsp = session.createNetworkSpecifierOpen(WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, new byte[]{(byte) 0xd2, (byte) 0xcf, 0x13, (byte) 0x4d, (byte) 0xd6, (byte) 0x3a});
                var netreq = new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                        .setNetworkSpecifier(netsp)
                        .build();
                getSystemService(ConnectivityManager.class).requestNetwork(netreq, new ConnectivityManager.NetworkCallback() {

                    @Override
                    public void onAvailable(@NonNull Network network) {
                        Log.d(TAG, "NAN datapath available: " + network);
                    }

                    @Override
                    public void onUnavailable() {
                        Log.d(TAG, "NAN datapath unavailable");
                    }
                });*/
                try {
                    session.subscribe(new SubscribeConfig.Builder()
                            .setServiceName("_ESP-Demo._udp")
                            .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)
                            .build(), new DiscoverySessionCallback() {

                        private SubscribeDiscoverySession sds;

                        @Override
                        public void onSubscribeStarted(@NonNull SubscribeDiscoverySession session) {
                            this.sds = session;
                        }

                        @Override
                        public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
                            Log.d(TAG, "NAN service discovered: " + peerHandle);
                            //sds.sendMessage(peerHandle, 0, "hello".getBytes());
                            CompletableFuture.runAsync(() -> {
                                try {
                                    Thread.sleep(1200);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }).thenAcceptAsync(unused -> {
                                startDatapath(peerHandle);
                            }, getMainExecutor());
                        }

                        @Override
                        public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
                        }

                        private void startDatapath(PeerHandle peerHandle) {
                            var netsp = new WifiAwareNetworkSpecifier.Builder(sds, peerHandle).build();
                            var netreq = new NetworkRequest.Builder()
                                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                                    .setNetworkSpecifier(netsp)
                                    .build();
                            getSystemService(ConnectivityManager.class).requestNetwork(netreq, new ConnectivityManager.NetworkCallback() {

                                private boolean testRun = false;

                                @Override
                                public void onAvailable(@NonNull Network network) {
                                    Log.d(TAG, "NAN datapath available: " + network);
                                }

                                @Override
                                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                                    if (!testRun) {
                                        Log.d(TAG, "Testing NAN socket");
                                        WifiAwareNetworkInfo ni = (WifiAwareNetworkInfo) networkCapabilities.getTransportInfo();
                                        AsyncUtils.runAsync(() -> {
                                            try (var socket = network.getSocketFactory().createSocket(ni.getPeerIpv6Addr(), 3333)) {
                                                socket.getOutputStream().write("hello".getBytes());
                                                var in = socket.getInputStream();
                                                byte[] resp = new byte[5];
                                                new DataInputStream(in).readFully(resp);
                                                Log.d(TAG, "NAN datapath test response: " + new String(resp, 0, resp.length));
                                            }
                                            getSystemService(ConnectivityManager.class).unregisterNetworkCallback(this);
                                        });
                                        testRun = true;
                                    }
                                }

                                @Override
                                public void onUnavailable() {
                                    Log.d(TAG, "NAN datapath unavailable");
                                }
                            });
                        }
                    }, null);
                } catch (SecurityException ex) {
                    Log.e(TAG, "NAN subscribe failed", ex);
                }
            }
        }, null);
    }

    private boolean hasBluetoothScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void checkTrustAndRunTest() {
        initTest();
        try (LwtAPIClient client = new LwtAPIClient(this, foundDevice.getAddress())) {
            client.setSocketWatchdogTimeout(Duration.ofSeconds(5));
            client.disableTLS();
            client.authenticateServer(GlobalTrustManager.getInstance(getApplication())).executeAsync().whenCompleteAsync((trusted, error) -> {
                if (error != null) {
                    Log.e(TAG, "Server auth operation error", error);
                    setButtonsEnabled(true);
                } else {
                    Log.i(TAG, "Server authentication result: " + trusted);
                }
            }, getMainExecutor());
            LwtSession session = client.newSession();
            enqueueTestOperations(client, session);
            executeOps(session);
        }
    }

    private void runTestOverTLS() {
        initTest();
        try (LwtAPIClient client = new LwtAPIClient(this, foundDevice.getAddress())) {
            client.setSocketWatchdogTimeout(Duration.ofSeconds(5));
            client.useTLS(
                    new LwtpTLSConfig.Builder(foundDevice.getAddress())
                            .setTLSPolicy(LwtpTLSPolicy.EXPLICIT_OPPORTUNISTIC)
                            .setSSLContext(sslContext)
                            .build()
            );
            LwtSession session = client.newSession();
            enqueueTestOperations(client, session);
            executeOps(session);
        }
    }

    private void initTest() {
        setButtonsEnabled(false);
        Log.i(TAG, "Testing bluetooth communication with device: " + foundDevice.getAddress());
    }

    private void enqueueTestOperations(LwtAPIClient client, LwtSession session) {
        for (int i = 0; i < 1; i++) {
            client.ping().enqueue(session).thenAccept(pingResponse -> {
                Log.i(TAG, "Ping response received: dev=" + pingResponse.deviceId() + ", time=" + pingResponse.deviceTime());
            }).exceptionally(ex -> {
                Log.e(TAG, "LWT operation failed", ex);
                return null;
            });
        }
        client.getTripRouteInfo().enqueue(session).thenAccept(tripRouteInfo -> {
            Log.i(TAG, "Trip route info received: " + routeInfoToString(tripRouteInfo));
        }).exceptionally(ex -> {
            Log.e(TAG, "LWT operation failed", ex);
            return null;
        });
        client.getTicketValidationInfo().enqueue(session).thenAccept(tvi -> {
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

    private void executeOps(LwtSession session) {
        session.executeAsync().whenCompleteAsync((unused, throwable) -> {
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
