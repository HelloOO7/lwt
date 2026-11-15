package cz.spojenka.lwdn;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
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

public class BluetoothLeAdvertisement implements AutoCloseable {

    private final Context context;
    private final AdvertisingSetParameters parameters;

    private AdvertiseData lastAdvData;
    private BluetoothLeAdvertiser currentAdvertiser;
    private AdvertisingSet currentAdvSet;
    private AdvertisingSetCallback callback;

    private final BroadcastReceiver bluetoothStateReceiver;

    private boolean isRunning = false;

    public BluetoothLeAdvertisement(Context context, AdvertisingSetParameters parameters) {
        this.context = context.getApplicationContext();
        this.parameters = parameters;
        bluetoothStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (isRunning) {
                    int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (newState == BluetoothAdapter.STATE_ON) {
                        tryStartAdvertiser();
                    } else if (newState != BluetoothAdapter.STATE_TURNING_ON) {
                        stopAdvertiser();
                    }
                }
            }
        };
    }

    /**
     * Check if advertising is supported on the LE hardware. Beware! When bluetooth is
     * off, this may return false even if the hardware actually supports advertising.
     * You should recheck this every time the Bluetooth state is changed.
     *
     * @param context context
     * @return true/false
     */
    public static boolean isSupported(Context context) {
        BluetoothAdapter adapter = BluetoothLeScan.getBluetoothAdapter(context);
        if (adapter != null) {
            return adapter.isMultipleAdvertisementSupported();
        }
        return false;
    }

    private BluetoothLeAdvertiser newBluetoothLeAdvertiser() {
        BluetoothAdapter adapter = BluetoothLeScan.getBluetoothAdapter(context);
        if (adapter != null) {
            return adapter.getBluetoothLeAdvertiser();
        }
        return null;
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            context.registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            if (lastAdvData != null) {
                tryStartAdvertiser();
            }
        }
    }

    public void stop() {
        if (isRunning) {
            isRunning = false;
            stopAdvertiser();
            context.unregisterReceiver(bluetoothStateReceiver);
        }
    }

    @SuppressLint("MissingPermission")
    public void advertise(AdvertiseData data) {
        this.lastAdvData = data;
        if (isRunning) {
            if (currentAdvSet != null) {
                if (hasAdvertisePermission()) {
                    currentAdvSet.setAdvertisingData(lastAdvData);
                }
            } else {
                tryStartAdvertiser();
            }
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
        AdvertiseData initialData = lastAdvData;
        currentAdvertiser.startAdvertisingSet(
                parameters,
                initialData,
                null,
                null,
                null,
                callback = new AdvertisingSetCallback() {

                    @SuppressLint("MissingPermission")
                    @Override
                    public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower, int status) {
                        if (this == callback) {
                            currentAdvSet = advertisingSet;
                            if (lastAdvData != initialData) {
                                // if data has changed between starting/started state, update it
                                currentAdvSet.setAdvertisingData(lastAdvData);
                            }
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
            currentAdvSet = null;
        }
    }

    @Override
    public void close() {
        stop();
    }
}
