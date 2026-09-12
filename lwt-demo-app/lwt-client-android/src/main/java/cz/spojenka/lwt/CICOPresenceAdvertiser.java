package cz.spojenka.lwt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import cz.spojenka.lwdn.util.BLEScanRecordUtil;
import cz.spojenka.lwdn.util.DeviceSpecifics;

public class CICOPresenceAdvertiser implements AutoCloseable {

    private final Context context;

    private AdvertiseData lastAdvData;
    private BluetoothLeAdvertiser currentAdvertiser;
    private AdvertisingSet currentAdvSet;
    private AdvertisingSetCallback callback;

    private final BroadcastReceiver bluetoothStateReceiver;

    private final Handler handler;
    private final Runnable updateAdvertisementsRunnable = this::updateAdvertisements;
    private boolean isRunning = false;

    private PresenceTrackingClient trackingClient;

    public CICOPresenceAdvertiser(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(context.getMainLooper());
        bluetoothStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (newState == BluetoothAdapter.STATE_ON) {
                    tryStartAdvertiser();
                } else if (newState != BluetoothAdapter.STATE_TURNING_ON) {
                    stopAdvertiser();
                }
            }
        };
        this.context.registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        trackingClient = new PresenceTrackingClient();
    }

    public static boolean isSupported(Context context) {
        BluetoothManager btm = context.getSystemService(BluetoothManager.class);
        if (btm != null) {
            BluetoothAdapter adapter = btm.getAdapter();
            if (adapter != null) {
                return adapter.isMultipleAdvertisementSupported();
            }
        }
        return false;
    }

    private BluetoothLeAdvertiser newBluetoothLeAdvertiser() {
        BluetoothManager btm = context.getSystemService(BluetoothManager.class);
        if (btm != null) {
            BluetoothAdapter adapter = btm.getAdapter();
            if (adapter != null) {
                return adapter.getBluetoothLeAdvertiser();
            }
        }
        return null;
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            updateAdvertisements();
        }
    }

    public void stop() {
        if (isRunning) {
            isRunning = false;
            handler.removeCallbacks(updateAdvertisementsRunnable);
            stopAdvertiser();
        }
    }

    public PresenceTrackingClient getTrackingClient() {
        return trackingClient;
    }

    private void updateAdvertisements() {
        advertise(buildAdvData());
        handler.postDelayed(updateAdvertisementsRunnable, trackingClient.getTotpPeriod().toMillis());
    }

    private byte[] buildAdvData() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.write(trackingClient.getClientId());
            dos.writeInt(trackingClient.getCurrentTotpPassword());
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("MissingPermission")
    private void advertise(byte[] data) {
        this.lastAdvData = new AdvertiseData.Builder()
                .addServiceData(new ParcelUuid(BLEScanRecordUtil.uuid32To128(LwtServiceConstants.BLE_SERVICE_UUID_CICO_KEEPALIVE)), data)
                .build();
        if (currentAdvSet != null) {
            if (hasAdvertisePermission()) {
                currentAdvSet.setAdvertisingData(lastAdvData);
            }
        } else {
            tryStartAdvertiser();
        }
    }

    private void tryStartAdvertiser() {
        if (callback != null || lastAdvData == null) {
            return;
        }
        if (currentAdvertiser == null) {
            currentAdvertiser = newBluetoothLeAdvertiser();
        }
        if (currentAdvertiser == null) {
            // bt not enabled
            return;
        }
        currentAdvertiser.startAdvertisingSet(
                buildAdvertiseSettings(),
                lastAdvData,
                null,
                null,
                null,
                callback = new AdvertisingSetCallback() {

                    @Override
                    public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
                        if (this == callback) {
                            currentAdvSet = advertisingSet;
                        }
                    }
                }
        );
    }

    private boolean hasAdvertisePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    @SuppressLint("MissingPermission")
    private void stopAdvertiser() {
        if (currentAdvertiser != null && callback != null) {
            if (hasAdvertisePermission()) {
                currentAdvertiser.stopAdvertisingSet(callback);
            }
            currentAdvertiser = null;
            callback = null;
        }
    }

    private AdvertisingSetParameters buildAdvertiseSettings() {
        AdvertisingSetParameters.Builder params = new AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setTxPowerLevel(-2) // higher than server's power of -7
                .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                .setConnectable(false)
                .setScannable(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            params.setDiscoverable(false);
        }

        return params.build();
    }

    @Override
    public void close() {
        stop();
    }
}
