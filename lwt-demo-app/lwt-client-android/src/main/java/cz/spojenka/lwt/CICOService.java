package cz.spojenka.lwt;

import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import cz.spojenka.lwdn.BluetoothLwdnScanner;
import cz.spojenka.lwdn.LwdnScanConfig;
import cz.spojenka.lwdn.LwdnScanException;

public class CICOService extends Service {

    private static final String EXTRA_NOTIFICATION_ID = CICOService.class.getName() + ".EXTRA_NOTIFICATION_ID";
    private static final String EXTRA_NOTIFICATION = CICOService.class.getName() + ".EXTRA_NOTIFICATION";
    private static final String EXTRA_CICO_TOKEN = CICOService.class.getName() + ".EXTRA_CICO_TOKEN";

    private static final String TAG = "BleScanService";

    private Handler handler;

    private int foregroundNotificationId;
    private Notification foregroundNotification;

    private LwtDeviceScanner scanner;
    private MutableLiveData<List<LwtDevice>> devicesInProximityLiveData = new MutableLiveData<>();
    private MutableLiveData<List<LwtDevice>> deviceResultTarget = devicesInProximityLiveData;

    private byte[] cicoToken;
    private CheckInIntermediate checkInIntermediate;
    private boolean isSessionActive = false;
    private LwtScan currentScan;
    private LwdnScanConfig.ScanMode lastScanMode;

    private LwtDevice currentDevice;
    private boolean restoreConnectionPending;
    private MutableLiveData<LwtDevice> currentDeviceLiveData = new MutableLiveData<>();
    private LwtAPIClient currentLwtClient;

    private MutableLiveData<CICOTicketFragment> currentTicketLiveData = new MutableLiveData<>();

    private Stack<DeviceStackEntry> deviceStack = new Stack<>();

    private final Runnable refreshTicketRunnable = this::refreshTicket;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(getMainLooper());
        BluetoothLwdnScanner btScanner = LwtDeviceScanner.createBluetoothScanner(getApplicationContext());
        if (btScanner == null) {
            throw new UnsupportedOperationException("Bluetooth scanning is not supported on this device (use isSupported() to check before starting the service)");
        }
        scanner = new LwtDeviceScanner(btScanner);
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopDeviceScanIfExists();
        disconnectCurrentDevice();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        foregroundNotificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        foregroundNotification = Objects.requireNonNull(IntentCompat.getParcelableExtra(intent, EXTRA_NOTIFICATION, Notification.class));
        cicoToken = Objects.requireNonNull(intent.getByteArrayExtra(EXTRA_CICO_TOKEN));

        return START_STICKY;
    }

    private LiveData<List<LwtDevice>> prepareSession() {
        assertSessionNotActive();
        MutableLiveData<List<LwtDevice>> resultList = new MutableLiveData<>();
        deviceResultTarget = resultList;
        startDeviceScan(LwdnScanConfig.ScanMode.LOW_LATENCY);
        return resultList;
    }

    private void cancelPrepareSession() {
        devicesInProximityLiveData = new MutableLiveData<>();
        assertSessionNotActive();
        stopDeviceScanIfExists();
    }

    private void onBluetoothRestarted() {
        if (isSessionActive) {
            if (currentDevice != null) {
                refreshTicket();
            } else {
                restoreConnection();
            }
        } else {
            startDeviceScanIfNotRunning();
        }
    }

    private void onBluetoothTurnedOff() {
        stopDeviceScanIfExists();
    }

    private void startDeviceScanIfNotRunning() {
        if (currentScan == null) {
            startDeviceScan(lastScanMode);
        }
    }

    private final LwtScan.OnResultListener scanResultListener = new LwtScan.OnResultListener() {
        @Override
        public void onResult(LwtScan scan, LwtDevice result) {
            devicesInProximityLiveData.setValue(getCicoDevicesByProximity(scan.getResults()));
            if (restoreConnectionPending) {
                restoreConnection();
            }
        }

        @Override
        public void onResultLost(LwtScan scan, LwtDevice result) {
            if (currentDevice != null && result.addressEquals(currentDevice)) {
                disconnectCurrentDevice();
                refreshTicket();
            }
        }

        @Override
        public void onFailure(LwtScan scan, LwdnScanException e) {

        }
    };

    private void startDeviceScan(LwdnScanConfig.ScanMode scanMode) {
        if (currentScan != null) {
            throw new IllegalStateException("Scan is already in progress");
        }
        currentScan = scanner.startScan(
                new LwdnScanConfig.Builder()
                        .setTimeout(null) //continuous scan
                        .setScanMode(scanMode)
                        .build()
        );
        lastScanMode = scanMode;
        currentScan.addOnResultListener(scanResultListener);
    }

    private void stopDeviceScan() {
        if (currentScan == null) {
            throw new IllegalStateException("No scan is in progress");
        }
        currentScan.removeOnResultListener(scanResultListener);
        currentScan.cancel();
    }

    private void stopDeviceScanIfExists() {
        if (currentScan != null) {
            stopDeviceScan();
        }
    }

    private void startForegroundService() {
        startForeground(foregroundNotificationId, foregroundNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
    }

    private void assertSessionActive() {
        if (!isSessionActive) {
            throw new IllegalStateException("No session is active");
        }
    }

    private void assertSessionNotActive() {
        if (isSessionActive) {
            throw new IllegalStateException("Session is already active");
        }
    }

    private CompletableFuture<CheckInIntermediate> requestSession(LwtDevice device) {
        assertSessionNotActive();
        disconnectCurrentDevice();
        connectDevice(device);
        return currentLwtClient
                .startCheckIn(cicoToken)
                .executeAsync()
                .whenCompleteAsync((intermediate, throwable) -> {
                    checkInIntermediate = intermediate;
                    if (throwable != null) {
                        Log.e(TAG, "Failed to request session with device " + device.getAddress(), throwable);
                        disconnectCurrentDevice();
                    }
                });
    }

    private boolean isDeviceCicoReady(LwtDevice device) {
        if (device instanceof LwtDevice.Vehicle v) {
            return v.getAdvData().isCanUseTicketing();
        }
        return false;
    }

    private CompletableFuture<CICOTicketFragment> startSession() {
        assertSessionNotActive();
        if (currentDevice == null || checkInIntermediate == null) {
            throw new IllegalStateException("Must successfully call requestSession() before starting a session");
        }
        return currentLwtClient
                .confirmCheckIn(checkInIntermediate)
                .executeAsync()
                .whenCompleteAsync((ticket, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Failed to start session with device " + currentDevice.getAddress(), throwable);
                        disconnectCurrentDevice();
                    } else {
                        cancelPrepareSession();
                        onGotTicket(ticket);
                        startForegroundService();
                        startIdleDeviceScan();
                    }
                }, getMainExecutor());
    }

    private void startIdleDeviceScan() {
        // switch to low power scan to check for device loss etc.
        deviceResultTarget = devicesInProximityLiveData;
        startDeviceScan(LwdnScanConfig.ScanMode.LOW_POWER);
    }

    private CompletableFuture<?> endSession() {
        assertSessionActive();
        stopDeviceScanIfExists();
        if (currentDevice != null) {
            CICOTicketFragment currentTicket = currentTicketLiveData.getValue();
            if (currentTicket != null) {
                return currentLwtClient
                        .checkOut(currentTicket)
                        .executeAsync()
                        .whenCompleteAsync((resp, throwable) -> {
                            if (throwable != null) {
                                Log.e(TAG, "Failed to check out with device " + currentDevice.getAddress(), throwable);
                            }
                            // session always ends regardless of whether the check-out request succeeded or failed
                            onSessionEnded();
                        }, getMainExecutor());
            } else {
                onSessionEnded();
            }
        } else {
            onSessionEnded();
        }
        return CompletableFuture.completedFuture(null);
    }

    private long ttlRatio(long ttl, float ratio) {
        return (long) (ttl * ratio);
    }

    private void onGotTicket(CICOTicketFragment ticket) {
        isSessionActive = true;
        currentTicketLiveData.setValue(ticket);

        long ttl = ticket.ttl();
        long ttlToRefresh = ttlRatio(ttl, 0.75f);

        handler.postDelayed(refreshTicketRunnable, ttlToRefresh);
    }

    private void onSessionEnded() {
        isSessionActive = false;
        currentTicketLiveData.setValue(null);
        stopSelf();
    }

    private CompletableFuture<?> forceDeviceChange(LwtDevice newDevice) {
        assertSessionActive();
        saveCurrentDevice();
        connectDevice(newDevice); // without disconnecting the current device
        return refreshTicket(false)
                .whenCompleteAsync((ticket, throwable) -> {
                    if (throwable != null) {
                        restorePreviousDevice();
                    } else {
                        discardPreviousDevice();
                    }
                }, getMainExecutor());
    }

    private void saveCurrentDevice() {
        deviceStack.push(new DeviceStackEntry(currentDevice, currentLwtClient));
    }

    private void restorePreviousDevice() {
        disconnectCurrentDevice();
        if (!deviceStack.isEmpty()) {
            DeviceStackEntry entry = deviceStack.pop();
            connectDevice(entry.device(), entry.client());
        }
    }

    private void discardPreviousDevice() {
        if (!deviceStack.isEmpty()) {
            DeviceStackEntry entry = deviceStack.pop();
            LwtAPIClient client = entry.client();
            if (client != null) {
                client.close();
            }
        }
    }

    private List<LwtDevice> getCicoDevicesByProximity(List<LwtDevice> source) {
        return source.stream()
                .filter(this::isDeviceCicoReady)
                .sorted(Comparator.comparingInt((LwtDevice d) -> d.getScanResult().rssi()).reversed())
                .collect(Collectors.toList());
    }

    private void connectDevice(LwtDevice device) {
        currentDevice = device;
        currentLwtClient = new LwtAPIClient(getApplicationContext(), device.getAddress());
        currentDeviceLiveData.setValue(currentDevice);
    }

    private void connectDevice(LwtDevice device, LwtAPIClient client) {
        currentDevice = device;
        currentLwtClient = client;
        currentDeviceLiveData.setValue(currentDevice);
    }

    private void disconnectCurrentDevice() {
        if (currentLwtClient != null) {
            currentLwtClient.close();
            currentLwtClient = null;
        }
        currentDevice = null;
        currentDeviceLiveData.setValue(null);
        handler.removeCallbacks(refreshTicketRunnable);
    }

    private CompletableFuture<CICOTicketFragment> refreshTicket() {
        return refreshTicket(true);
    }

    private CompletableFuture<CICOTicketFragment> refreshTicket(boolean changeDeviceIfLost) {
        assertSessionActive();
        CICOTicketFragment currentTicket = currentTicketLiveData.getValue();
        if (currentTicket == null) {
            throw new IllegalStateException("No ticket to refresh");
        }
        return currentLwtClient
                .refreshCICO(currentTicket)
                .executeAsync()
                .whenCompleteAsync((newTicket, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Failed to refresh ticket with device " + currentDevice.getAddress(), throwable);
                        if (changeDeviceIfLost) {
                            disconnectCurrentDevice();
                            restoreConnection();
                        }
                    } else {
                        onGotTicket(newTicket);
                    }
                }, getMainExecutor());
    }

    private void restoreConnection() {
        if (currentDevice != null) {
            throw new IllegalStateException("A device is already connected.");
        }
        List<LwtDevice> closestDevices = devicesInProximityLiveData.getValue();
        if (closestDevices == null || closestDevices.isEmpty()) {
            restoreConnectionPending = true;
            return;
        }
        CICOTicketFragment currentTicket = currentTicketLiveData.getValue();
        if (currentTicket == null) {
            throw new IllegalStateException("Can not restore connection without a ticket");
        }
        restoreConnectionPending = false;
        List<CompletableFuture<?>> attemptFutures = new ArrayList<>();
        for (int i = 0; i < closestDevices.size(); i++) {
            attemptFutures.add(new CompletableFuture<>());
        }
        for (int i = 0; i < closestDevices.size(); i++) {
            LwtDevice dev = closestDevices.get(i);
            int devIndex = i;
            Runnable tryNextRunnable = () -> {
                LwtAPIClient client = new LwtAPIClient(getApplicationContext(), dev.getAddress());
                try {
                    client
                            .refreshCICO(currentTicket)
                            .executeAsync()
                            .whenCompleteAsync((newTicket, throwable) -> {
                                if (throwable == null) {
                                    Log.i(TAG, "Successfully restored connection, now using device " + currentDevice.getAddress());
                                    connectDevice(dev, client);
                                    onGotTicket(newTicket);
                                    attemptFutures.get(devIndex).complete(null);
                                } else {
                                    Log.e(TAG, "Attempt to restore connection using device " + currentDevice.getAddress() + " failed", throwable);
                                    client.close();
                                    attemptFutures.get(devIndex).completeExceptionally(throwable);
                                }
                            });
                } catch (Throwable th) {
                    // close client if we failed to start the operation
                    client.close();
                    throw th;
                }
            };
            if (i == 0) {
                tryNextRunnable.run();
            } else {
                attemptFutures.get(i - 1).exceptionally(ex -> {
                    tryNextRunnable.run();
                    return null;
                });
            }
        }
    }

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            int oldState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_ON) {
                onBluetoothRestarted();
            } else if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                onBluetoothTurnedOff();
            } else if (state == BluetoothAdapter.STATE_OFF) {
                if (oldState != BluetoothAdapter.STATE_TURNING_OFF) {
                    onBluetoothTurnedOff();
                }
            }
        }
    };

    public static boolean isSupported(Context context) {
        return BluetoothLwdnScanner.isSupported(context);
    }

    public static Intent startIntent(Context context, byte[] cicoToken, ForegroundServiceConfig foregroundServiceConfig) {
        return new Intent(context, CICOService.class)
                .putExtra(EXTRA_CICO_TOKEN, cicoToken)
                .putExtra(EXTRA_NOTIFICATION_ID, foregroundServiceConfig.notificationId())
                .putExtra(EXTRA_NOTIFICATION, foregroundServiceConfig.notification());
    }

    public static Intent stopIntent(Context context) {
        return new Intent(context, CICOService.class);
    }

    public static Intent bindIntent(Context context) {
        return new Intent(context, CICOService.class);
    }

    private static class LocalBinder extends Binder implements ICICOService {

        private final CICOService service;

        public LocalBinder(CICOService service) {
            this.service = service;
        }

        @Override
        public LiveData<List<LwtDevice>> prepareSession() {
            return service.prepareSession();
        }

        @Override
        public void cancelPrepareSession() {
            service.cancelPrepareSession();
        }

        @Override
        public CompletableFuture<?> requestSession(LwtDevice device) {
            return service.requestSession(device);
        }

        @Override
        public CompletableFuture<?> startSession() {
            return service.startSession();
        }

        @Override
        public CompletableFuture<?> endSession() {
            return service.endSession();
        }

        @Override
        public boolean isSessionActive() {
            return service.isSessionActive;
        }

        @Override
        public LiveData<List<LwtDevice>> getDevicesInProximityLiveData() {
            return service.devicesInProximityLiveData;
        }

        @Override
        public CompletableFuture<?> forceDeviceChange(LwtDevice device) {
            return service.forceDeviceChange(device);
        }

        @Override
        public LiveData<LwtDevice> getCurrentDeviceLiveData() {
            return service.currentDeviceLiveData;
        }

        @Override
        public LiveData<CICOTicketFragment> getCurrentTicketLiveData() {
            return service.currentTicketLiveData;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder(this);
    }

    private static record DeviceStackEntry(LwtDevice device, LwtAPIClient client) {

    }

    public static record ForegroundServiceConfig(int notificationId, Notification notification) {

    }
}
