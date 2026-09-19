package cz.spojenka.lwt.cicomock.controllers;

import cz.spojenka.lwt.cicomock.api.lwtclient.LwtServiceConstants;
import cz.spojenka.lwt.cicomock.api.lwtclient.TripAdvertisementData;
import cz.spojenka.lwt.cicomock.api.lwtclient.TripAdvertisementDataExt;
import cz.spojenka.lwt.cicomock.model.MockState;
import cz.spojenka.lwt.cicomock.model.NearbyDevice;
import cz.spojenka.lwt.cicomock.services.AdvDataConverter;
import cz.spojenka.lwt.cicomock.services.DeviceScanThread;
import cz.spojenka.lwt.cicomock.services.EventSenderService;
import cz.spojenka.lwt.cicomock.services.PresenceTrackingThread;
import cz.spojenka.lwt.ticketing.api.CICOEventBatch;
import cz.spojenka.lwt.ticketing.api.CICOEventPush;
import cz.spojenka.lwt.ticketing.api.CICOEventType;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import tools.jackson.databind.util.ByteBufferBackedInputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;

@Component
public class ClientWSHandler extends BinaryWebSocketHandler implements MockState.Observer {

    private static final int MSG_START_SCAN = 1;
    private static final int MSG_STOP_SCAN = 2;
    private static final int MSG_SCAN_RESULT = 3;
    private static final int MSG_CHANGE_TIMESCALE = 4;
    private static final int MSG_SERVICE_EVENT = 5;
    private static final int MSG_TRY_CONNECT = 6;

    private static final int DEVICE_EVENT_CHECK_IN = 1;
    private static final int DEVICE_EVENT_REFRESH = 2;
    private static final int DEVICE_EVENT_CHECK_OUT = 3;
    private static final int DEVICE_EVENT_TRACK_PRESENCE = 4;

    private static final byte[] PRESENCE_TRACKING_OFF_ADDRESS = new byte[6];

    private final EventSenderService eventSender;
    private final MockState state;
    private final DeviceScanThread scanThread;

    private final List<WebSocketSession> currentSessions = new ArrayList<>();
    private final Map<WebSocketSession, Integer> scanRefCounts = new HashMap<>();
    private final Map<WebSocketSession, PresenceTrackingThread> presenceTrackingThreads = new HashMap<>();
    private final ObjectProvider<PresenceTrackingThread> pttProvider;

    @Value("${mock.scan.min-report-rssi}")
    private int minReportRssi;
    @Value("${mock.scan.min-comm-rssi}")
    private int minCommRssi;

    public ClientWSHandler(EventSenderService eventSender, MockState state, DeviceScanThread scanThread, ObjectProvider<PresenceTrackingThread> pttProvider) {
        this.eventSender = eventSender;
        this.state = state;
        this.scanThread = scanThread;
        this.pttProvider = pttProvider;

        this.scanThread.start();
        state.addObserver(this);
    }

    @Override
    public synchronized void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        currentSessions.add(session);
        sendTimescale(session);
    }

    @Override
    public synchronized void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        currentSessions.remove(session);
        scanRefCounts.remove(session);
        PresenceTrackingThread ptt = presenceTrackingThreads.remove(session);
        if (ptt != null) {
            ptt.end();
        }
    }

    @Override
    protected void handleBinaryMessage(@NonNull WebSocketSession session, @NonNull BinaryMessage message) throws Exception {
        var buf = message.getPayload();
        int code = buf.get();
        switch (code) {
            case MSG_SERVICE_EVENT -> {
                int eventType = buf.get();
                if (eventType == DEVICE_EVENT_TRACK_PRESENCE) {
                    byte[] trackingDevAddr = new byte[6];
                    buf.get(trackingDevAddr);
                    UUID sessionId = readUUID(buf);
                    if (Arrays.equals(trackingDevAddr, PRESENCE_TRACKING_OFF_ADDRESS)) {
                        trackingDevAddr = null;
                    }
                    getPresenceTrackingThread(session, sessionId).changeTrackingDevice(trackingDevAddr);
                } else {
                    sendSingleMOSEvent(unpackCicoEvent(eventType, buf));
                }
            }
            case MSG_START_SCAN -> {
                synchronized (this) {
                    incrementScanRefCount(session);
                }
            }
            case MSG_STOP_SCAN -> {
                synchronized (this) {
                    decrementScanRefCount(session);
                }
            }
            case MSG_TRY_CONNECT -> {
                handleTransaction(session, code, buf, req -> {
                    byte[] devAddr = new byte[6];
                    req.get(devAddr);
                    var dev = state.findNearbyDeviceByAddress(devAddr);
                    boolean ok = false;
                    if (dev != null) {
                        int rssi = AdvDataConverter.computeDeviceRssi(dev, state.getLocation());
                        if (rssi >= minCommRssi) {
                            ok = true;
                        }
                    }
                    return ByteBuffer.allocate(1).put((byte) (ok ? 1 : 0));
                });
            }
        };
    }

    public void onPresenceTrackingLost(UUID sessionId, String lwtMetadata) {
        OffsetDateTime time = OffsetDateTime.now();
        CICOEventPush event = new CICOEventPush(
                UUID.randomUUID(),
                null,
                sessionId,
                0,
                time.toInstant().toEpochMilli(),
                time,
                CICOEventType.BE_OUT,
                lwtMetadata
        );
        try {
            sendSingleMOSEvent(event);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendSingleMOSEvent(CICOEventPush event) throws IOException, InterruptedException {
        eventSender.sendEventsToMOS(new CICOEventBatch(List.of(event), Instant.now().toEpochMilli()));
    }

    private PresenceTrackingThread getPresenceTrackingThread(WebSocketSession session, UUID sessionId) {
        return presenceTrackingThreads.computeIfAbsent(session, s -> {
            PresenceTrackingThread t = pttProvider.getObject();
            t.configure(sessionId, this);
            t.start();
            return t;
        });
    }

    private void incrementScanRefCount(WebSocketSession session) {
        scanRefCounts.compute(session, (s, count) -> count == null ? 1 : count + 1);
    }

    private void decrementScanRefCount(WebSocketSession session) {
        Integer current = scanRefCounts.get(session);
        if (current == null) {
            return;
        }
        --current;
        if (current <= 0) {
            scanRefCounts.remove(session);
        } else {
            scanRefCounts.put(session, current);
        }
    }

    private CICOEventPush unpackCicoEvent(int type, ByteBuffer buf) {
        long accountId = buf.getLong();
        UUID sessionId = readUUID(buf);
        UUID eventId =  readUUID(buf);
        UUID lastEventId = readUUID(buf);
        long timeMillis =  buf.getLong();
        int timeOffset = buf.getInt();
        OffsetDateTime time = Instant.ofEpochMilli(timeMillis).atOffset(ZoneOffset.ofTotalSeconds(timeOffset));
        String lwtMetadata = readString(buf);

        return new CICOEventPush(eventId, lastEventId, sessionId, accountId, timeMillis, time, convertEventType(type), lwtMetadata);
    }

    private CICOEventType convertEventType(int type) {
        return switch (type) {
            case DEVICE_EVENT_CHECK_IN -> CICOEventType.CHECK_IN;
            case DEVICE_EVENT_REFRESH -> CICOEventType.REFRESH;
            case DEVICE_EVENT_CHECK_OUT -> CICOEventType.CHECK_OUT;
            default -> throw new IllegalArgumentException("Unknown event type: " + type);
        };
    }

    private UUID readUUID(ByteBuffer buf) {
        return new UUID(buf.getLong(), buf.getLong());
    }

    private String readString(ByteBuffer buf) {
        try (DataInputStream in = new DataInputStream(new ByteBufferBackedInputStream(buf))) {
            return in.readUTF();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onTimescaleChanged(float timescale) {
        try {
            broadcastTimescale();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleTransaction(WebSocketSession wss, int messageType, ByteBuffer request, Function<ByteBuffer, ByteBuffer> handler) throws IOException {
        int txId = request.getInt();
        ByteBuffer resp = handler.apply(request);
        resp.rewind();
        ByteBuffer respWithTxId = ByteBuffer.allocate(Integer.BYTES + resp.remaining());
        respWithTxId.putInt(txId).put(resp);
        respWithTxId.flip();
        sendMessage(wss, messageType, respWithTxId);
    }

    private void broadcastTimescale() throws IOException {
        sendTimescale(null);
    }

    private void sendTimescale(WebSocketSession session) throws IOException {
        sendMessage(session, MSG_CHANGE_TIMESCALE, ByteBuffer.allocate(Float.BYTES).putFloat(state.getTimescale()));
    }

    private void sendMessage(WebSocketSession session, int type, ByteBuffer data) throws IOException {
        if (session == null) {
            for (WebSocketSession s : currentSessions) {
                sendMessage(s, type, data);
            }
            return;
        }
        data.rewind();
        ByteBuffer dataWithType = ByteBuffer.allocate(1 + data.remaining());
        dataWithType.put((byte) type);
        dataWithType.put(data);
        dataWithType.flip();

        session.sendMessage(new BinaryMessage(dataWithType));
    }

    @Override
    public void onNearbyDevicesChanged(List<NearbyDevice> nearbyDevices) {
        if (scanRefCounts.isEmpty()) {
            return;
        }
        try {
            for (NearbyDevice dev : nearbyDevices) {
                int rssi = AdvDataConverter.computeDeviceRssi(dev, state.getLocation());
                if (rssi < minReportRssi) {
                    continue;
                }
                for (WebSocketSession session : currentSessions) {
                    if (scanRefCounts.getOrDefault(session, 0) > 0) {
                        broadcastScanResult(session, dev.address(), rssi, dev.advData());
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void broadcastScanResult(WebSocketSession session, byte[] deviceAddress, int rssi, TripAdvertisementData scanData) throws IOException {
        byte[] content;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(out)) {
            dos.write(deviceAddress);
            dos.writeByte(rssi);
            dos.writeByte(1); // 1 service
            dos.writeByte(Integer.BYTES);
            if (scanData instanceof TripAdvertisementDataExt) {
                dos.writeInt(LwtServiceConstants.BLE_SERVICE_UUID_VEHICLE_EXTENDED);
            } else {
                dos.writeInt(LwtServiceConstants.BLE_SERVICE_UUID_VEHICLE_BASE);
            }
            byte[] tripData = TripAdvertisementData.wrap(scanData);
            dos.writeShort(tripData.length);
            dos.write(tripData);
            content = out.toByteArray();
        }
        sendMessage(session, MSG_SCAN_RESULT, ByteBuffer.wrap(content));
    }
}
