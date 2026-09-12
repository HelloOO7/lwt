package cz.spojenka.lwt;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.IntentCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import cz.spojenka.lwdn.BluetoothLeThrottling;
import cz.spojenka.lwdn.BluetoothLwdnScanner;
import cz.spojenka.lwdn.BluetoothLwdnSocket;
import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwdn.LwdnScanConfig;
import cz.spojenka.lwdn.LwdnScanException;
import cz.spojenka.lwdn.ScanErrorCode;
import cz.spojenka.lwt.util.LwtTime;
import cz.spojenka.lwtp.LwtpTLSConfig;
import cz.spojenka.lwtp.LwtpTLSPolicy;

public class CICOService extends Service {

    private static final String EXTRA_FOREGROUND_CONTROLLER_CLASS = CICOService.class.getName() + ".EXTRA_FOREGROUND_CONTROLLER_CLASS";

    private static final String TAG = "CICOService";

    private Handler handler;

    private ForegroundController foregroundController;
    private PowerManager.WakeLock wakeLock;

    private CICOPersistence persistence;

    private LwtDeviceScanner scanner;
    private MutableLiveData<List<LwtDevice>> devicesInProximityLiveData = new MutableLiveData<>();
    private MutableLiveData<List<LwtDevice>> deviceResultTarget = devicesInProximityLiveData;

    private CheckInIntermediate checkInIntermediate;
    private boolean isSessionActive = false;
    private LwtScan currentScan;
    private LwdnScanConfig.ScanMode lastScanMode;

    private LwtDevice currentDevice;
    private boolean isCommOnline = true;
    private boolean restoreConnectionPending;
    private MutableLiveData<LwtDevice> currentDeviceLiveData = new MutableLiveData<>();
    private LwtAPIClient currentLwtClient;
    private long nextAllowedSocketOpenTime;
    private SSLContext clientSSLContext;

    private MutableLiveData<CICOTicketFragment> currentTicketLiveData = new MutableLiveData<>();
    private long ttlForTryRestore;

    private Stack<DeviceStackEntry> deviceStack = new Stack<>();

    private final Runnable refreshTicketRunnable = this::refreshTicket;
    private final Runnable restartThrottledScanRunnable = this::restartLastDeviceScan;

    private Consumer<BluetoothLwdnSocket> globalConnectObserver;

    private CICOPresenceAdvertiser presenceAdvertiser;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Starting CICO service");
        handler = new Handler(getMainLooper());
        persistence = CICOPersistence.getInstance(this);
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CICOService:WakeLock");
        BluetoothLwdnScanner btScanner = LwtDeviceScanner.createBluetoothScanner(this);
        if (btScanner == null) {
            throw new UnsupportedOperationException("Bluetooth scanning is not supported on this device (use isSupported() to check before starting the service)");
        }
        scanner = new LwtDeviceScanner(btScanner);
        isCommOnline = btScanner.isAvailable();
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        globalConnectObserver = socket -> {
            // on successful connection, reset this.
            resetHuaweiThrottling();
        };
        BluetoothLwdnSocket.addGlobalConnectObserver(globalConnectObserver);
        if (CICOPresenceAdvertiser.isSupported(this)) {
            Log.d(TAG, "Presence advertiser is supported");
            presenceAdvertiser = new CICOPresenceAdvertiser(this);
        } else {
            Log.d(TAG, "Presence advertiser is not supported");
        }
        Log.d(TAG, "Service started");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            Log.d(TAG, "Terminating CICO service");
            stopAndClearDeviceScan();
            disconnectCurrentDevice();
            unregisterReceiver(bluetoothStateReceiver);
            BluetoothLwdnSocket.removeGlobalConnectObserver(globalConnectObserver);
            Log.d(TAG, "Service terminated");
        } finally {
            releaseWakeLock(); // always release wakelock
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Received onStartCommand intent=" + intent);
        boolean isRevivedBySystem = intent == null;
        Class<?> foregroundControllerClass;
        if (isRevivedBySystem) {
            if (FeaturePrerequisite.BACKGROUND_LOCATION_FOR_LE_SCAN.isApplicable(this)) {
                if (!FeaturePrerequisite.BACKGROUND_LOCATION_FOR_LE_SCAN.check(this)) {
                    // can not restart, because we do not have BG location permission needed to start
                    // LE scan from the background
                    return START_STICKY;
                }
            }
            foregroundControllerClass = persistence.getLastForegroundController();
            if (foregroundControllerClass == null) {
                return START_STICKY;
            }
        } else {
            foregroundControllerClass = Objects.requireNonNull(IntentCompat.getSerializableExtra(intent, EXTRA_FOREGROUND_CONTROLLER_CLASS, Class.class));
        }
        foregroundController = instantiateForegroundController(foregroundControllerClass);
        foregroundController.onServiceStateChanged(lastServiceState);

        persistence.putLastForegroundController(foregroundControllerClass);

        if (isRevivedBySystem && !isSessionActive && persistence.wasLastSessionTerminatedUnexpectedly()) {
            CICOTicketFragment ticket = persistence.getLastTicket();
            if (ticket != null) {
                Log.d(TAG, "Service was revived after process death, restoring persisted state");
                onGotTicket(ticket);
                onSessionStarted();
                refreshTicket(true);
            }
        }

        return START_STICKY;
    }

    private ForegroundController instantiateForegroundController(Class<?> controllerClass) {
        try {
            return (ForegroundController) controllerClass.getConstructor(Context.class).newInstance(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate foreground controller " + controllerClass.getName(), e);
        }
    }

    private LiveData<List<LwtDevice>> prepareSession() {
        Log.d(TAG, "prepareSession()");
        assertSessionNotActive();
        if (currentScan != null) {
            return deviceResultTarget;
        }
        Log.d(TAG, "Scan is not yet running, starting it");
        MutableLiveData<List<LwtDevice>> resultList = new MutableLiveData<>();
        deviceResultTarget = resultList;
        startDeviceScan(LwdnScanConfig.ScanMode.LOW_LATENCY);
        return resultList;
    }

    private void cancelPrepareSession() {
        Log.d(TAG, "cancelPrepareSession");
        devicesInProximityLiveData = new MutableLiveData<>();
        assertSessionNotActive();
        stopAndClearDeviceScan();
    }


    private boolean isPrepareSessionRunning() {
        return !isSessionActive && currentScan != null;
    }

    private void onBluetoothRestarted() {
        Log.d(TAG, "Bluetooth was turned on after being off");
        updateCommOnline(true);
        if (isSessionActive) {
            if (currentDevice != null) {
                refreshTicket();
            } else {
                restoreConnection();
            }
            foregroundController.onServiceErrorResolved(ErrorCode.BLUETOOTH_TURNED_OFF);
        } else {
            restartLastDeviceScan();
        }
    }

    private void onBluetoothTurnedOff() {
        Log.d(TAG, "User turned off bluetooth");
        updateCommOnline(false);
        // clear scan results until we are back online
        devicesInProximityLiveData.setValue(List.of());
        stopDeviceScanIfExists();
        cancelPendingTicketRefresh();
        if (isSessionActive) {
            foregroundController.onServiceError(ErrorCode.BLUETOOTH_TURNED_OFF);
        }
    }

    private void updateCommOnline(boolean commOnline) {
        this.isCommOnline = commOnline;
        updateServiceState();
    }

    private void stopAndClearDeviceScan() {
        Log.d(TAG, "stopAndClearDeviceScan()");
        stopDeviceScanIfExists();
        lastScanMode = null;
        handler.removeCallbacks(restartThrottledScanRunnable);
    }

    private void restartLastDeviceScan() {
        Log.d(TAG, "restartLastDeviceScan() requested");
        if (currentScan == null && lastScanMode != null) {
            startDeviceScan(lastScanMode);
        } else {
            Log.d(TAG, "no active scan");
        }
    }

    private Set<LwdnAddress> devicesToAddresses(Collection<LwtDevice> devices) {
        return devices.stream()
                .map(LwtDevice::getAddress)
                .collect(Collectors.toSet());
    }

    private boolean sameDevices(List<LwtDevice> list1, List<LwtDevice> list2) {
        if (list1 == null || list2 == null) {
            return false;
        }
        if (list1.size() != list2.size()) {
            return false;
        }
        return devicesToAddresses(list1).equals(devicesToAddresses(list2));
    }

    private final LwtScan.OnResultListener scanResultListener = new LwtScan.OnResultListener() {

        private long restoreThrottleTime;

        @Override
        public void onResult(LwtScan scan, LwtDevice result) {
            List<LwtDevice> lastDevices = deviceResultTarget.getValue();
            List<LwtDevice> newDevices = getCicoDevicesByProximity(scan.getResults());
            deviceResultTarget.setValue(newDevices);
            if (isRestoreConnectionPendingAndPossible()) {
                if (!sameDevices(lastDevices, newDevices)) {
                    restoreThrottleTime = 0;
                }
                if (restoreThrottleTime == 0 || SystemClock.elapsedRealtime() - restoreThrottleTime > ttlForTryRestore) {
                    restoreThrottleTime = SystemClock.elapsedRealtime();
                    Log.d(TAG, "restoreConnection on result");
                    restoreConnection();
                }
            } else {
                restoreThrottleTime = 0;
            }
            if (currentDevice != null && result.addressEquals(currentDevice)) {
                if (!isDeviceCicoReady(result, false)) {
                    Log.d(TAG, "Got result from device, but it is not CICO ready (probably off-route?); considering it lost");
                    onResultLost(scan, result);
                } else {
                    updateCurrentDevice(result); // update rssi and advdata
                }
            }
        }

        @Override
        public void onResultLost(LwtScan scan, LwtDevice result) {
            deviceResultTarget.setValue(getCicoDevicesByProximity(scan.getResults()));
            if (currentDevice != null && result.addressEquals(currentDevice)) {
                Log.d(TAG, "Current device " + result.getAddress() + " was lost.");
                disconnectCurrentDevice();
                if (isSessionActive) {
                    restoreConnection();
                }
            }
        }

        @Override
        public void onFailure(LwtScan scan, LwdnScanException e) {
            if (e.getCode() == ScanErrorCode.THROTTLED) {
                Log.e(TAG, "Scan throttled, will restart after throttle period", e);
                long currentTime = SystemClock.elapsedRealtime();
                long nextScanTime = BluetoothLeThrottling.getNextUnthrottledScanTime(CICOService.this);
                Log.i(TAG, "Current time: " + currentTime + ", next scan time: " + nextScanTime);
                stopDeviceScan(); // do not clear
                handler.postAtTime(restartThrottledScanRunnable, realtimeToUptime(nextScanTime));
            } else {
                Log.e(TAG, "Scan failed", e);
                stopAndClearDeviceScan();
            }
        }
    };

    private static long realtimeToUptime(long realtime) {
        return SystemClock.uptimeMillis() + (realtime - SystemClock.elapsedRealtime());
    }

    private void startDeviceScan(LwdnScanConfig.ScanMode scanMode) {
        if (currentScan != null) {
            throw new IllegalStateException("Scan is already in progress");
        }
        Log.d(TAG, "startDeviceScan(" + scanMode + ")");
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
        Log.d(TAG, "stopDeviceScan()");
        currentScan.removeOnResultListener(scanResultListener);
        currentScan.cancel();
        currentScan = null;
    }

    private void stopDeviceScanIfExists() {
        if (currentScan != null) {
            stopDeviceScan();
        }
    }

    private void stopPresenceAdvertiser() {
        if (presenceAdvertiser != null) {
            Log.d(TAG, "Stop presence advertiser");
            presenceAdvertiser.close();
        }
    }

    private void startForegroundService() {
        Log.d(TAG, "Starting foreground service");
        // update this now, which makes the foreground controller show the notification (state broadcast)
        updateStateFlag(ServiceState.FLAG_FOREGROUND_SERVICE_ACTIVE, true);
        // only now start the foreground service. this is to ensure that foregroundController.createNotification()
        // returns an up-to-date notification. it is needed because sometimes, the notification from startForeground
        // is shown out of order, and so we want to be sure that it always has the latest possible state.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, foregroundController.getNotificationId(), foregroundController.createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(foregroundController.getNotificationId(), foregroundController.createNotification());
        }
        acquireWakeLock();
    }

    @SuppressLint("WakelockTimeout")
    private void acquireWakeLock() {
        wakeLock.acquire();
    }

    private void stopForegroundService() {
        Log.d(TAG, "Stopping foreground service");
        releaseWakeLock();
        stopForeground(STOP_FOREGROUND_REMOVE);
        updateStateFlag(ServiceState.FLAG_FOREGROUND_SERVICE_ACTIVE, false);
    }

    private void releaseWakeLock() {
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
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

    private LwtCall<CheckInIntermediate> requestSessionCall = null;

    private CompletableFuture<CheckInIntermediate> requestSession(LwtDevice device, byte[] cicoToken) {
        Log.d(TAG, "requestSession(" + device.getAddress() + ", XXXXXXX)");
        assertSessionNotActive();
        disconnectCurrentDevice();
        connectDevice(device);
        requestSessionCall = currentLwtClient.startCheckIn(cicoToken);
        return requestSessionCall
                .executeAsync()
                .whenCompleteAsync((intermediate, throwable) -> {
                    if (throwable instanceof CancellationException) {
                        return; // cancelled
                    }
                    Log.d(TAG, "requestSession has arrived");
                    requestSessionCall = null;
                    checkInIntermediate = intermediate;
                    if (throwable != null) {
                        Log.e(TAG, "Failed to request session with device " + device.getAddress(), throwable);
                        disconnectCurrentDevice();
                    }
                }, getMainExecutor());
    }

    private void cancelRequestSession() {
        Log.d(TAG, "cancelRequestSession()");
        assertSessionNotActive();
        if (requestSessionCall != null) {
            requestSessionCall.cancel();
            requestSessionCall = null;
        }
        disconnectCurrentDevice();
    }

    private boolean isDeviceCicoReady(LwtDevice device, boolean mustAllowCheckIn) {
        if (device instanceof LwtDevice.Vehicle v) {
            return v.getAdvData().isCanUseCICO() && (!mustAllowCheckIn || v.getAdvData().isCanUseTicketing());
        }
        return false;
    }

    private CompletableFuture<CICOTicketFragment> startSession() {
        assertSessionNotActive();
        if (currentDevice == null || checkInIntermediate == null) {
            throw new IllegalStateException("Must successfully call requestSession() before starting a session");
        }
        Log.d(TAG, "startSession()");
        LwtDevice sessionDevice = currentDevice;
        return currentLwtClient
                .confirmCheckIn(checkInIntermediate, getPresenceTrackingClient())
                .executeAsync()
                .whenCompleteAsync((ticket, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Failed to start session with device " + sessionDevice.getAddress(), throwable);
                    } else {
                        Log.d(TAG, "startSession OK");
                        onGotTicket(ticket);
                        // better to call after onGotTicket, so that the first time a session
                        // becomes active, there is always a valid ticket
                        onSessionStarted();
                        if (currentDevice == null || !currentDevice.addressEquals(sessionDevice)) {
                            // device was lost or changed while session was starting
                            if (currentDevice == null) {
                                restoreConnection();
                            } else {
                                refreshTicket();
                            }
                        }
                    }
                }, getMainExecutor());
    }

    private void onSessionStarted() {
        Log.d(TAG, "onSessionStarted()");
        cancelPrepareSession();
        startForegroundService();
        startIdleDeviceScan();
        startPresenceAdvertiser();
        setSessionActive(true);
    }

    private void startPresenceAdvertiser() {
        if (presenceAdvertiser != null) {
            Log.d(TAG, "Start presence advertiser");
            presenceAdvertiser.start();
        }
    }

    private void startIdleDeviceScan() {
        Log.d(TAG, "Start idle device scan");
        // switch to low power scan to check for device loss etc.
        deviceResultTarget = devicesInProximityLiveData;
        startDeviceScan(LwdnScanConfig.ScanMode.LOW_POWER);
    }

    private CompletableFuture<?> endSession() {
        Log.d(TAG, "endSession()");
        assertSessionActive();
        stopAndClearDeviceScan();
        if (getCurrentDeviceIfOnline() != null) {
            CICOTicketFragment currentTicket = currentTicketLiveData.getValue();
            if (currentTicket != null) {
                Log.d(TAG, "endSession has ticket, run check-out");
                return currentLwtClient
                        .checkOut(currentTicket)
                        .executeAsync()
                        .whenCompleteAsync((resp, throwable) -> {
                            if (throwable != null) {
                                Log.e(TAG, "Failed to check out with device " + currentDevice.getAddress(), throwable);
                            } else {
                                Log.d(TAG, "Checked out.");
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
        Log.d(TAG, "Got ticket, issuedAt=" + LwtTime.convertOffsetDateTime(ticket.issuedAtAbsolute()));
        updateTicketLiveData(ticket);

        long ttl = ticket.ttl();
        long ttlToRefresh = ttlRatio(ttl, 0.75f);
        ttlForTryRestore = ttlRatio(ttl, 0.1f);

        handler.postDelayed(refreshTicketRunnable, ttlToRefresh);
    }

    private void updateTicketLiveData(CICOTicketFragment ticket) {
        currentTicketLiveData.setValue(ticket);
        persistence.putLastTicket(ticket);
        updateServiceState();
    }

    private void onSessionEnded() {
        Log.d(TAG, "onSessionEnded()");
        setSessionActive(false);
        updateTicketLiveData(null);
        stopPresenceAdvertiser();
        stopForegroundService();
        stopSelf();
    }

    private void setSessionActive(boolean active) {
        isSessionActive = active;
        persistence.putSessionActive(active);
        updateStateFlag(ServiceState.FLAG_SESSION_ACTIVE, active);
    }

    private CompletableFuture<?> forceDeviceChange(LwtDevice newDevice) {
        Log.d(TAG, "forceDeviceChange(" + newDevice.getAddress() + ")");
        if (!isCommOnline) {
            CompletableFuture<Void> fail = new CompletableFuture<>();
            fail.completeExceptionally(new IOException("Bluetooth is off, can not communicate with devices"));
            return fail;
        }
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
                .filter(dev -> isDeviceCicoReady(dev, true))
                .sorted(Comparator.comparingInt((LwtDevice d) -> d.getScanResult().rssi()).reversed())
                .collect(Collectors.toList());
    }

    private LwtAPIClient createDeviceClient(LwtDevice device) {
        LwtAPIClient client = new LwtAPIClient(this, device.getAddress());

        if (clientSSLContext != null) {
            client.useTLS(
                    new LwtpTLSConfig.Builder(device.getAddress())
                            .setTLSPolicy(LwtpTLSPolicy.EXPLICIT_REQUIRED)
                            .setSSLContext(clientSSLContext)
                            .build()
            );
        } else {
            Log.w(TAG, "TLS is not configured, communication will be insecure");
        }

        return client;
    }

    private void connectDevice(LwtDevice device) {
        connectDevice(device, createDeviceClient(device));
    }

    private void connectDevice(LwtDevice device, LwtAPIClient client) {
        Log.d(TAG, "connectDevice(" + device.getAddress() + ")");
        currentLwtClient = client;
        updateCurrentDevice(device);
    }

    private void disconnectCurrentDevice() {
        Log.d(TAG, "disconnectCurrentDevice()");
        if (currentLwtClient != null) {
            currentLwtClient.close();
            currentLwtClient = null;
        }
        if (currentDevice != null) {
            Log.d(TAG, "Device was disconnected: " + currentDevice.getAddress());
        }
        updateCurrentDevice(null);
        cancelPendingTicketRefresh();
    }

    private void updateCurrentDevice(LwtDevice device) {
        currentDevice = device;
        currentDeviceLiveData.setValue(currentDevice);
        updateServiceState();
    }

    private LwtDevice getCurrentDeviceIfOnline() {
        return isCommOnline ? currentDevice : null;
    }

    private int stateFlags = 0;
    private ServiceState lastServiceState = new ServiceState(stateFlags, null, null);

    private void updateServiceState() {
        ServiceState newState = new ServiceState(stateFlags, getCurrentDeviceIfOnline(), currentTicketLiveData.getValue());
        if (!newState.equals(lastServiceState)) {
            lastServiceState = newState;
            publishServiceState();
        }
    }

    private void updateStateFlag(int flag, boolean isSet) {
        int old = stateFlags;
        if (isSet) {
            stateFlags |= flag;
        } else {
            stateFlags &= ~flag;
        }
        if (old != stateFlags) {
            updateServiceState();
        }
    }

    private void publishServiceState() {
        if (foregroundController != null) {
            foregroundController.onServiceStateChanged(lastServiceState);
        }
    }

    private void cancelPendingTicketRefresh() {
        handler.removeCallbacks(refreshTicketRunnable);
    }

    private PresenceTrackingClient getPresenceTrackingClient() {
        if (presenceAdvertiser != null) {
            return presenceAdvertiser.getTrackingClient();
        }
        return null;
    }

    private CompletableFuture<CICOTicketFragment> refreshTicket() {
        return refreshTicket(true);
    }

    private CompletableFuture<CICOTicketFragment> refreshTicket(boolean changeDeviceIfLost) {
        Log.d(TAG, "refreshTicket(changeDeviceIfLost=" + changeDeviceIfLost + ")");
        assertSessionActive();
        CICOTicketFragment currentTicket = currentTicketLiveData.getValue();
        if (currentTicket == null) {
            throw new IllegalStateException("No ticket to refresh");
        }
        if (currentDevice == null) {
            if (!changeDeviceIfLost) {
                throw new IllegalStateException("Attempt to refresh ticket with change of device disallowed, but no device is connected!");
            }
            restoreConnection();
            return CompletableFuture.completedFuture(null);
        } else {
            return currentLwtClient
                    .refreshCICO(currentTicket, getPresenceTrackingClient())
                    .executeAsync()
                    .whenCompleteAsync((newTicket, throwable) -> {
                        if (throwable != null) {
                            boolean throttled = handleHuaweiThrottling(throwable);
                            Log.e(TAG, "Failed to refresh ticket with device " + currentDevice.getAddress(), throwable);
                            if (changeDeviceIfLost) {
                                if (!throttled) {
                                    disconnectCurrentDevice();
                                    restoreConnection();
                                } else {
                                    refreshTicketAfterThrottling();
                                }
                            }
                        } else {
                            Log.d(TAG, "refreshTicket OK");
                            onGotTicket(newTicket);
                        }
                    }, getMainExecutor());
        }
    }

    private void refreshTicketAfterThrottling() {
        if (nextAllowedSocketOpenTime == 0) {
            refreshTicket();
        } else {
            handler.postDelayed(refreshTicketRunnable, nextAllowedSocketOpenTime - System.currentTimeMillis());
        }
    }

    private void restoreConnection() {
        Log.d(TAG, "restoreConnection");
        if (currentDevice != null) {
            throw new IllegalStateException("A device is already connected.");
        }
        List<LwtDevice> closestDevices = devicesInProximityLiveData.getValue();
        if (closestDevices == null || closestDevices.isEmpty()) {
            Log.d(TAG, "No device to restore connection with, deferring restore");
            setRestoreConnectionPending();
            return;
        }
        CICOTicketFragment currentTicket = currentTicketLiveData.getValue();
        if (currentTicket == null) {
            throw new IllegalStateException("Can not restore connection without a ticket");
        }
        resetRestoreConnectionPending();
        List<CompletableFuture<?>> attemptFutures = new ArrayList<>();
        for (int i = 0; i < closestDevices.size(); i++) {
            attemptFutures.add(new CompletableFuture<>());
        }
        Log.d(TAG, closestDevices.size() + " candidates for new connection");
        for (int i = 0; i < closestDevices.size(); i++) {
            LwtDevice dev = closestDevices.get(i);
            int devIndex = i;
            Runnable tryNextRunnable = () -> {
                Log.d(TAG, "Attempting connection to " + dev.getAddress());
                LwtAPIClient client = createDeviceClient(dev);
                try {
                    client
                            .refreshCICO(currentTicket, getPresenceTrackingClient())
                            .executeAsync()
                            .whenCompleteAsync((newTicket, throwable) -> {
                                if (throwable == null) {
                                    Log.i(TAG, "Successfully restored connection, now using device " + dev.getAddress());
                                    connectDevice(dev, client);
                                    onGotTicket(newTicket);
                                    attemptFutures.get(devIndex).complete(null);
                                } else {
                                    Log.e(TAG, "Attempt to restore connection using device " + dev.getAddress() + " failed", throwable);
                                    client.close();
                                    attemptFutures.get(devIndex).completeExceptionally(throwable);
                                }
                            }, getMainExecutor());
                } catch (Throwable th) {
                    Log.e(TAG, "Send restore request failed", th);
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
        attemptFutures.get(attemptFutures.size() - 1).whenCompleteAsync((o, throwable) -> {
            if (throwable != null) {
                Log.e(TAG, "Failed to connect to any device", throwable);
                setRestoreConnectionPending();
                handleHuaweiThrottling(throwable);
            }
        }, getMainExecutor());
    }

    private void resetRestoreConnectionPending() {
        restoreConnectionPending = false;
    }

    private void setRestoreConnectionPending() {
        restoreConnectionPending = true;
    }

    private boolean isRestoreConnectionPendingAndPossible() {
        if (restoreConnectionPending) {
            if (nextAllowedSocketOpenTime == 0 || System.currentTimeMillis() > nextAllowedSocketOpenTime) {
                return true;
            }
        }
        return false;
    }

    private void resetHuaweiThrottling() {
        nextAllowedSocketOpenTime = 0;
    }

    private boolean handleHuaweiThrottling(Throwable throwable) {
        if (BluetoothLeThrottling.isHuaweiConnectionThrottled(throwable)) {
            if (nextAllowedSocketOpenTime == 0) {
                Log.w(TAG, "Huawei background connect throttling detected, deferring connection");
                // huawei uses currentTimeMillis
                nextAllowedSocketOpenTime = System.currentTimeMillis() + BluetoothLeThrottling.getHuaweiConnectionThrottlePeriod();
            }
            return true;
        }
        return false;
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

    public static Intent startIntent(Context context, Class<? extends ForegroundController> foregroundControllerClass) {
        return new Intent(context, CICOService.class)
                .putExtra(EXTRA_FOREGROUND_CONTROLLER_CLASS, foregroundControllerClass);
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
        public void initSecureContext(SSLContext sslContext) {
            service.clientSSLContext = sslContext;
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
        public boolean isPrepareSessionRunning() {
            return service.isPrepareSessionRunning();
        }

        @Override
        public CompletableFuture<?> requestSession(LwtDevice device, byte[] cicoToken) {
            return service.requestSession(device, cicoToken);
        }

        @Override
        public void cancelRequestSession() {
            service.cancelRequestSession();
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
        public boolean isConnectedToDevice() {
            return service.currentDevice != null;
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

    /**
     * Interface that must be implemented to handle foreground service notifications.
     * The class implementing this interface must have a constructor that takes a Context as a parameter.
     */
    public interface ForegroundController {

        public int getNotificationId();

        public Notification createNotification();

        public void onServiceStateChanged(ServiceState newState);

        public void onServiceError(ErrorCode errorCode);

        public void onServiceErrorResolved(ErrorCode errorCode);
    }

    public static record ServiceState(int stateFlags, @Nullable LwtDevice currentDevice,
                                      @Nullable CICOTicketFragment currentTicket) {

        public static int FLAG_SESSION_ACTIVE = (1 << 0);
        public static int FLAG_FOREGROUND_SERVICE_ACTIVE = (1 << 1);

        public boolean hasFlag(int flags) {
            return (stateFlags() & flags) == flags;
        }
    }

    public static enum ErrorCode {
        BLUETOOTH_TURNED_OFF
    }
}
