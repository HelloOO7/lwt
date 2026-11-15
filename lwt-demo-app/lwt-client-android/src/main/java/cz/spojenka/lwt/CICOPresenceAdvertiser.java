package cz.spojenka.lwt;

import android.annotation.SuppressLint;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetParameters;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import cz.spojenka.lwdn.BluetoothLeAdvertisement;

public class CICOPresenceAdvertiser implements AutoCloseable {

    private BluetoothLeAdvertisement advertisement;

    private final Handler handler;
    private final Runnable updateAdvertisementsRunnable = this::updateAdvertisements;
    private boolean isRunning = false;

    private PresenceTrackingClient trackingClient;

    public CICOPresenceAdvertiser(Context context) {
        this.handler = new Handler(context.getMainLooper());
        advertisement = new BluetoothLeAdvertisement(context, buildAdvertiseSettings());

        trackingClient = new PresenceTrackingClient();
    }

    public static boolean isSupported(Context context) {
        return BluetoothLeAdvertisement.isSupported(context);
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            advertisement.start();
            updateAdvertisements();
        }
    }

    public void stop() {
        if (isRunning) {
            advertisement.stop();
            handler.removeCallbacks(updateAdvertisementsRunnable);
            isRunning = false;
        }
    }

    public PresenceTrackingClient getTrackingClient() {
        return trackingClient;
    }

    public void resetTrackingClientId() {
        trackingClient.resetClientId();
        updateAdvertisements();
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
        AdvertiseData advData = new AdvertiseData.Builder()
                .addServiceData(new ParcelUuid(LwtServiceConstants.BLE_SERVICE_UUID_CICO_KEEPALIVE.uuid()), data)
                .build();

        advertisement.advertise(advData);
    }

    private AdvertisingSetParameters buildAdvertiseSettings() {
        AdvertisingSetParameters.Builder params = new AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setTxPowerLevel(1) // higher than server's power of -7
                .setInterval(AdvertisingSetParameters.INTERVAL_HIGH / 2) // every 500 ms (ensures swift detection on huawei)
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
