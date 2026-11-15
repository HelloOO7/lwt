package cz.spojenka.lwt;

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.LruCache;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import androidx.lifecycle.MutableLiveData;
import cz.spojenka.lwdn.BluetoothLeAdvertisement;
import cz.spojenka.lwdn.BluetoothLeScan;
import cz.spojenka.lwdn.util.BLEScanRecordUtil;
import kotlin.text.HexExtensionsKt;
import kotlin.text.HexFormat;

public class InspectionSyncTransceiver implements Closeable {

    private static final String TAG = "InspectionSync";

    private static final Duration DEVICE_LOST_TIMEOUT = Duration.ofSeconds(10);

    private static final int SERVICE_ID_INT = 0x49534E43; // 'ISNC'
    private static final ParcelUuid SERVICE_UUID = new ParcelUuid(BLEScanRecordUtil.uuid32To128(SERVICE_ID_INT));

    private static final int PEER_ID_NONE = 0;

    /**
     * Placeholder ticket hash used as a heartbeat to notify neighbors of presence even if
     * no ticket has been scanned yet.
     */
    private static final int TICKET_HASH_HEARTBEAT = -1;

    private final Context context;

    private final Delegate delegate;

    private Handler handler;
    private BluetoothLeScan currentLeScan;
    private final Runnable refreshAdvRunnable = this::updateAdvertisementLoop;
    private boolean isAdvertising = false;
    private final BluetoothLeAdvertisement advertisement;

    private int lastAdvTicketHash = TICKET_HASH_HEARTBEAT;
    private LruCache<Integer, Set<Integer>> ticketHashesSeenBy = new LruCache<>(100);

    private LruCache<Integer, DeviceSeenRecord> devicesLastSeen = new LruCache<>(100);

    private final MutableLiveData<Set<Integer>> nearbyDevicesLiveData = new MutableLiveData<>(Set.of());

    public InspectionSyncTransceiver(Context context, Delegate delegate) {
        this.context = context;
        this.delegate = delegate;
        handler = new Handler(context.getMainLooper());
        advertisement = new BluetoothLeAdvertisement(context, buildAdvertiseSettings());
    }

    public void start() {
        if (currentLeScan != null) {
            return;
        }

        boolean swFiltering = BluetoothLeScan.shouldUseSoftwareUUIDFiltering(context);
        List<ScanFilter> filters = new ArrayList<>();
        if (!swFiltering) {
            filters.add(new ScanFilter.Builder().setServiceData(SERVICE_UUID, new byte[0], new byte[0]).build());
        }

        currentLeScan = new BluetoothLeScan.Builder(context)
                .setFilters(filters)
                .setSettings(
                        new ScanSettings.Builder()
                                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                                .setLegacy(true)
                                .build()
                )
                .build();

        currentLeScan.addOnResultListener(new BluetoothLeScan.OnResultListener() {
            @Override
            public void onResult(BluetoothLeScan scan, ScanResult result) {
                if (result.getScanRecord() != null) {
                    byte[] serviceData = result.getScanRecord().getServiceData(SERVICE_UUID);
                    if (serviceData != null) {
                        try {
                            handleAdvertisementReceived(serviceData);
                        } catch (IllegalArgumentException ex) {
                            Log.e(
                                    TAG,
                                    "Could not decrypt payload from "
                                            + result.getDevice().getAddress() + ": "
                                            + hex(serviceData),
                                    ex
                            );
                        }
                    }
                }
            }

            @Override
            public void onFailure(BluetoothLeScan scan, Exception e) {
                Log.e(TAG, "Could not start scan", e);
            }
        });

        currentLeScan.start();
        startAdvertising();
    }

    public void stop() {
        stopAdvertising();
        if (currentLeScan != null) {
            currentLeScan.stop();
            currentLeScan = null;
        }
    }

    @Override
    public void close() {
        stop();
        handler.removeCallbacksAndMessages(null);
    }

    public void setLastAdvTicketHash(int ticketHash) {
        lastAdvTicketHash = ticketHash;
        if (ticketHash != TICKET_HASH_HEARTBEAT) {
            putSeenTicketHash(delegate.getDeviceID(), ticketHash);
        }
        if (isAdvertising) {
            updateAdvertisementLoop();
        }
    }

    public Set<Integer> checkTicketHashAlreadyScanned(int ticketHash) {
        Set<Integer> res = ticketHashesSeenBy.get(ticketHash);
        if (res == null) {
            res = Set.of();
        }
        return res;
    }

    public boolean isSeenOnlyHere(Set<Integer> seenByResult) {
        return Set.of(delegate.getDeviceID()).equals(seenByResult);
    }

    private void continueAdvertisementLoop() {
        handler.postDelayed(refreshAdvRunnable, delegate.getTimeSliceSize().toMillis());
    }

    private void stopAdvertisementLoop() {
        handler.removeCallbacks(refreshAdvRunnable);
    }

    private void updateAdvertisementLoop() {
        stopAdvertisementLoop();
        updateAdvertisement();
        continueAdvertisementLoop();
    }

    private AdvertisingSetParameters buildAdvertiseSettings() {
        AdvertisingSetParameters.Builder params = new AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setInterval(AdvertisingSetParameters.INTERVAL_MEDIUM)
                .setConnectable(false)
                .setScannable(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            params.setDiscoverable(false);
        }

        return params.build();
    }

    private void startAdvertising() {
        if (isAdvertising) {
            return;
        }
        isAdvertising = true;
        updateAdvertisement();
        advertisement.start();
        continueAdvertisementLoop();
    }

    private void stopAdvertising() {
        if (!isAdvertising) {
            return;
        }
        advertisement.stop();
        stopAdvertisementLoop();
        isAdvertising = false;
    }

    private void updateAdvertisement() {
        byte[] serviceData = encryptPayload(
                ByteBuffer
                        .allocate(2 * Integer.BYTES)
                        .putInt(delegate.getDeviceID())
                        .putInt(lastAdvTicketHash)
                        .array()
        );

        if (serviceData != null) {
            advertisement.advertise(new AdvertiseData.Builder().addServiceData(SERVICE_UUID, serviceData).build());
        }
    }

    private void handleAdvertisementReceived(byte[] serviceData) {
        byte[] decrypted = decryptPayload(serviceData);
        ByteBuffer buf = ByteBuffer.wrap(decrypted);
        int peerId = buf.getInt();
        if (peerId != PEER_ID_NONE) {
            putSeenDevice(peerId);
        }
        int ticketHash = buf.getInt();
        if (ticketHash == TICKET_HASH_HEARTBEAT) {
            return;
        }
        putSeenTicketHash(peerId, ticketHash);
    }

    public MutableLiveData<Set<Integer>> getNearbyDevicesLiveData() {
        return nearbyDevicesLiveData;
    }

    private void putSeenDevice(int devId) {
        var current = devicesLastSeen.get(devId);
        if (current != null) {
            handler.removeCallbacks(current.expiryCallback());
        }

        Runnable expiryCallback = () -> {
            devicesLastSeen.remove(devId);
            updateNearbyDevices();
        };

        devicesLastSeen.put(devId, new DeviceSeenRecord(Instant.now(), expiryCallback));

        updateNearbyDevices();

        handler.postDelayed(expiryCallback, DEVICE_LOST_TIMEOUT.toMillis());
    }

    private void updateNearbyDevices() {
        Instant maxDeviceAge = Instant.now().minus(DEVICE_LOST_TIMEOUT);

        var liveDevices = devicesLastSeen
                .snapshot()
                .entrySet()
                .stream()
                .filter(dev -> dev.getValue().lastSeen().isAfter(maxDeviceAge) && dev.getKey() != delegate.getDeviceID())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (!liveDevices.equals(nearbyDevicesLiveData.getValue())) {
            nearbyDevicesLiveData.setValue(liveDevices);
        }
    }

    private void putSeenTicketHash(int devId, int ticketHash) {
        Set<Integer> seenBy = ticketHashesSeenBy.get(ticketHash);
        if (seenBy == null) {
            seenBy = new HashSet<>();
            ticketHashesSeenBy.put(ticketHash, seenBy);
        }
        seenBy.add(devId);
    }

    private byte[] encryptPayload(byte[] payload) {
        try {
            byte[] key = delegate.provideKeyForTime(Instant.now(), Crypto.KEY_SIZE);
            if (key == null) {
                return null;
            }
            //Log.d(TAG, "Encrypting payload with key " + hex(key));
            return Crypto.encryptAndWrap(payload, key);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] decryptPayload(byte[] payload) {
        GeneralSecurityException finalEx = null;
        Instant curTime = Instant.now();
        for (int i = -1; i <= 1; i++) {
            Instant keyTime = curTime.minus(delegate.getTimeSliceSize().multipliedBy(i));
            try {
                byte[] key = delegate.provideKeyForTime(keyTime, Crypto.KEY_SIZE);
                if (key != null) {
                    //Log.d(TAG, "Trying key " + hex(key));
                    return Crypto.unwrapAndDecrypt(payload, key);
                }
            } catch (GeneralSecurityException ex) {
                if (finalEx == null) {
                    finalEx = ex;
                }
            }
        }
        throw new IllegalArgumentException("Could not decrypt payload with any known keys - may be forged?", finalEx);
    }

    private String hex(byte[] bytes) {
        return HexExtensionsKt.toHexString(bytes, HexFormat.Companion.getDefault());
    }

    public static interface Delegate {

        public int getDeviceID();

        public Duration getTimeSliceSize();

        public byte[] provideKeyForTime(Instant time, int keySize);
    }

    private static class Crypto {

        public static final String ALGORITHM = "AES";
        public static final String CIPHER = "AES/CCM/NoPadding";
        public static final int KEY_SIZE = 16;
        public static final int NONCE_SIZE = 8;
        private static final int TAG_SIZE_BITS = 64;
        private static final int TAG_SIZE_BYTES = 8;

        private static final SecureRandom RANDOM = new SecureRandom();

        public static byte[] encryptAndWrap(byte[] payload, byte[] key) throws GeneralSecurityException {
            byte[] nonce = new byte[NONCE_SIZE];
            RANDOM.nextBytes(nonce);
            Cipher cipher = initCipher(Cipher.ENCRYPT_MODE, key, nonce);
            byte[] encryptedPayload = cipher.doFinal(payload);

            return ByteBuffer.allocate(NONCE_SIZE + encryptedPayload.length)
                    .put(nonce)
                    .put(encryptedPayload)
                    .array();
        }

        private static Cipher initCipher(int opmode, byte[] key, byte[] nonce) throws GeneralSecurityException {
            SecretKey secretKey = new SecretKeySpec(key, ALGORITHM);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(opmode, secretKey, new GCMParameterSpec(TAG_SIZE_BITS, nonce));
            return cipher;
        }

        public static byte[] unwrapAndDecrypt(byte[] payload, byte[] key) throws GeneralSecurityException {
            if (payload.length < NONCE_SIZE + TAG_SIZE_BYTES) {
                throw new IllegalArgumentException("Payload too short");
            }
            Cipher cipher = initCipher(Cipher.DECRYPT_MODE, key, Arrays.copyOf(payload, NONCE_SIZE));
            return cipher.doFinal(payload, NONCE_SIZE, payload.length - NONCE_SIZE);
        }
    }

    private static record DeviceSeenRecord(Instant lastSeen, Runnable expiryCallback) {

    }
}
