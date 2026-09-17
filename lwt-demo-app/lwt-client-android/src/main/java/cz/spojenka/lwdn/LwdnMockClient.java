package cz.spojenka.lwdn;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.spojenka.lwdn.util.BLEScanRecordUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.Buffer;
import okio.ByteString;

public class LwdnMockClient extends WebSocketListener implements Closeable {

    private static final String TAG = "LwdnMockClient";

    private static final int MSG_START_SCAN = 1;
    private static final int MSG_STOP_SCAN = 2;
    private static final int MSG_SCAN_RESULT = 3;
    private static final int MSG_CHANGE_TIMESCALE = 4;
    private static final int MSG_SERVICE_EVENT = 5;
    private static final int MSG_TRY_CONNECT = 6;

    private final WebSocket ws;

    private final List<Observer> observers = new ArrayList<>();
    private final List<ScanClient> scanListeners = new ArrayList<>();

    private float currentTimescale = 1.0f;

    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    private AtomicInteger transactionId = new AtomicInteger(1);
    private final List<TransactionWaiter> transactionWaiters = new ArrayList<>();

    public LwdnMockClient(String wsUrl) {
        OkHttpClient client = new OkHttpClient.Builder().build();
        ws = client.newWebSocket(new Request.Builder().url(wsUrl).build(), this);
    }

    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        super.onOpen(webSocket, response);
        Log.d(TAG, "Connected to mock server");
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
        super.onFailure(webSocket, t, response);
        Log.e(TAG, "Mock server op failed", t);
    }

    public synchronized void addObserver(Observer observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
            mainThreadHandler.post(() -> observer.onTimescaleChanged(currentTimescale));
        }
    }

    public synchronized void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    public float getCurrentTimescale() {
        return currentTimescale;
    }

    public Duration translateDuration(Duration sourceDuration) {
        return Duration.ofMillis((long) (sourceDuration.toMillis() / currentTimescale));
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
        ByteBuffer bb = bytes.asByteBuffer();
        int type = Byte.toUnsignedInt(bb.get());
        if (type == MSG_SCAN_RESULT) {
            onScanResult(bb);
        } else if (type == MSG_CHANGE_TIMESCALE) {
            onTimescaleChanged(bb);
        } else if (type == MSG_TRY_CONNECT) {
            handleTransactionResponse(bb);
        }
    }

    private synchronized void onTimescaleChanged(ByteBuffer data) {
        float timescale = data.getFloat();
        this.currentTimescale = timescale;
        mainThreadHandler.post(() -> {
            for (Observer observer : observers) {
                observer.onTimescaleChanged(timescale);
            }
        });
    }

    private synchronized void onScanResult(ByteBuffer buf) {
        byte[] devAddr = new byte[6];
        buf.get(devAddr);
        byte rssi = buf.get();
        int serviceCount = Byte.toUnsignedInt(buf.get());
        Map<UUID, byte[]> serviceData = new HashMap<>();
        for (int i = 0; i < serviceCount; i++) {
            int uuidLength = buf.get();
            UUID uuid = readUUID(buf, uuidLength);
            int dataLength = Short.toUnsignedInt(buf.getShort());
            byte[] serviceDataBytes = new byte[dataLength];
            buf.get(serviceDataBytes);
            serviceData.put(uuid, serviceDataBytes);
        }
        LwdnAddress addr = new MockLwdnAddress(devAddr);
        //Log.d(TAG, "Received scan result " + addr);
        for (ScanClient client : scanListeners) {
            var mapped = tryMapResultForClient(serviceData, client);
            if (!mapped.isEmpty()) {
                mainThreadHandler.post(() -> {
                    client.scan().addResult(new LwdnScanResult(addr, rssi, mapped));
                });
            }
        }
    }

    private Map<LwdnServiceID, byte[]> tryMapResultForClient(Map<UUID, byte[]> serviceData, ScanClient client) {
        Map<LwdnServiceID, byte[]> result = new HashMap<>();
        for (LwdnServiceID sid : client.serviceIDS()) {
            if (sid instanceof LwdnServiceID.BluetoothUUID btUuid) {
                byte[] data = serviceData.get(btUuid.uuid());
                if (data != null) {
                    result.put(sid, data);
                }
            }
        }
        return result;
    }

    private UUID readUUID(ByteBuffer buffer, int len) {
        return switch (len) {
            case Short.BYTES -> BLEScanRecordUtil.uuid16To128(buffer.getShort());
            case Integer.BYTES -> BLEScanRecordUtil.uuid32To128(buffer.getInt());
            case 16 -> BLEScanRecordUtil.parseUUID128(buffer);
            default -> throw new IllegalArgumentException("Invalid UUID length: " + len);
        };
    }

    private ScanClient findScanClient(LwdnScan scan) {
        for (ScanClient client : scanListeners) {
            if (client.scan() == scan) {
                return client;
            }
        }
        return null;
    }

    public synchronized void startMockDeviceScan(List<LwdnServiceID> serviceIDs, LwdnScan implScan) {
        if (findScanClient(implScan) != null) {
            return;
        }
        ScanClient scanClient = new ScanClient(serviceIDs, implScan);
        if (scanListeners.isEmpty()) {
            if (!sendBinaryMessage(MSG_START_SCAN, null)) {
                implScan.markFailed(new LwdnScanException(ScanErrorCode.NOT_ENABLED, "WS is closed"));
                return;
            }
        }
        scanListeners.add(scanClient);
    }

    public synchronized void stopMockDeviceScan(LwdnScan implScan) {
        if (scanListeners.remove(findScanClient(implScan))) {
            if (scanListeners.isEmpty()) {
                sendBinaryMessage(MSG_STOP_SCAN, null);
            }
        }
    }

    public void sendClientEvent(int eventType, byte[] message) {
        ByteBuffer content = ByteBuffer.allocate(1 + (message != null ? message.length : 0));
        content.put((byte) eventType);
        if (message != null) {
            content.put(message);
        }
        sendBinaryMessage(MSG_SERVICE_EVENT, content);
    }

    public boolean attemptConnectToDevice(byte[] deviceAddress) throws IOException {
        return transceive(MSG_TRY_CONNECT, ByteBuffer.wrap(deviceAddress), Duration.ofSeconds(1)).get() == 1;
    }

    @Override
    public synchronized void close() {
        ws.close(1000, "Client closed");
    }

    private boolean sendBinaryMessage(int type, ByteBuffer content) {
        if (content == null) {
            content = ByteBuffer.allocate(0);
        } else {
            content.rewind();
        }
        ByteString bs;
        try (Buffer buf = new Buffer()) {
            bs = buf
                    .writeByte(type)
                    .write(ByteString.of(content))
                    .readByteString();
        }
        return ws.send(bs);
    }

    private ByteBuffer transceive(int messageType, ByteBuffer content, Duration timeout) throws IOException {
        content.rewind();
        int txId = transactionId.getAndIncrement();
        ByteBuffer contentWithTxId = ByteBuffer.allocate(Integer.BYTES + content.remaining());
        contentWithTxId.putInt(txId).put(content);
        if (!sendBinaryMessage(messageType, contentWithTxId)) {
            throw new IOException("Failed to send tx message");
        }
        TransactionWaiter waiter = new TransactionWaiter();
        waiter.txId = txId;
        synchronized (transactionWaiters) {
            transactionWaiters.add(waiter);
        }
        return waiter.waitForResult(timeout);
    }

    private void handleTransactionResponse(ByteBuffer data) {
        int txId = data.getInt();
        synchronized (transactionWaiters) {
            for (TransactionWaiter waiter : transactionWaiters) {
                if (waiter.txId == txId) {
                    transactionWaiters.remove(waiter);
                    waiter.submitResult(data);
                    break;
                }
            }
        }
    }

    private static record ScanClient(List<LwdnServiceID> serviceIDS, LwdnScan scan) {

    }

    public static interface Observer {

        public void onTimescaleChanged(float newTimescale);
    }

    private static class TransactionWaiter {

        public int txId;
        public ByteBuffer result;

        private synchronized ByteBuffer waitForResult(Duration timeout) throws IOException {
            try {
                wait(timeout.toMillis());
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            if (result == null) {
                throw new SocketTimeoutException("timed out after waiting " + timeout);
            }
            return result;
        }

        private synchronized void submitResult(ByteBuffer result) {
            this.result = result;
            notifyAll();
        }
    }
}
