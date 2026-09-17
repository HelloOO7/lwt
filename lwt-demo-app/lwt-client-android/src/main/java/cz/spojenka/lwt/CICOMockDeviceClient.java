package cz.spojenka.lwt;

import com.google.flatbuffers.FlatBufferBuilder;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import cz.spojenka.lwdn.LwdnMockClient;
import cz.spojenka.lwdn.util.BLEScanRecordUtil;
import cz.spojenka.lwt.util.ByteBufferUtils;
import cz.spojenka.lwt.util.FlatbufferUtils;
import cz.spojenka.lwt.util.LwtTime;

public class CICOMockDeviceClient implements ICICODeviceClient {

    private static final int DEVICE_EVENT_CHECK_IN = 1;
    private static final int DEVICE_EVENT_REFRESH = 2;
    private static final int DEVICE_EVENT_CHECK_OUT = 3;
    private static final int DEVICE_EVENT_TRACK_PRESENCE = 4;

    private static final byte[] PRESENCE_TRACKING_OFF_ADDRESS = new byte[6];

    private final LwtDevice device;
    private final LwdnMockClient mockServerClient;

    public CICOMockDeviceClient(LwtDevice device, LwdnMockClient mockServerClient) {
        this.device = device;
        this.mockServerClient = mockServerClient;
    }

    private <T> T repackFbb(FlatBufferBuilder fbb, Class<T> objType) {
        return FlatbufferUtils.reflectOpenFlatbuffer(fbb.dataBuffer(), objType);
    }

    private <T> CompletableFuture<T> futureAfterSuccessfulConnect(Supplier<T> supplier) {
        return CompletableFuture.runAsync(() -> {
           try {
               if (!mockServerClient.attemptConnectToDevice(device.getAddress().getRawLinkAddress())) {
                   throw new CompletionException(new IOException("Can not connect to device " + device.getAddress()));
               }
           } catch (IOException ex) {
               throw new CompletionException(ex);
           }
        }).thenApply(unused -> supplier.get());
    }

    @Override
    public CompletableFuture<CheckInIntermediate> startCheckIn(byte[] checkInToken) {
        return futureAfterSuccessfulConnect(() -> {
            FlatBufferBuilder fbb = new FlatBufferBuilder();
            // put session id to confirmation token
            fbb.finish(CheckInIntermediate.createCheckInIntermediate(fbb, fbb.createByteVector(BLEScanRecordUtil.uuidToBytes(UUID.randomUUID()))));
            return repackFbb(fbb, CheckInIntermediate.class);
        });
    }

    private String convertLocationStateFromAdv(int advLocationState) {
        return switch (advLocationState) {
            case TripAdvertisementData.LOCATION_STATE_AT_STOP -> ".";
            case TripAdvertisementData.LOCATION_STATE_BETWEEN_STOPS -> "=";
            case TripAdvertisementData.LOCATION_STATE_BEFORE_STOP -> ">";
            case TripAdvertisementData.LOCATION_STATE_AFTER_STOP -> "<";
            default -> throw new IllegalArgumentException("Unknown location state: " + advLocationState);
        };
    }

    private String createLwtMetadata() {
        List<String> parts = new ArrayList<>();
        if (device instanceof LwtDevice.Vehicle vehicle) {
            TripAdvertisementData advData = vehicle.getAdvData();
            if (advData.getLineLicenseNumber() != 0) {
                String tk = Integer.toString(advData.getLineLicenseNumber());
                if (advData.getTripNumber() != 0) {
                    tk += "/" + advData.getTripNumber();
                }
                parts.add("TK:" + tk);
            }
            if (advData.getStopDepTime() != null) {
                parts.add("TTD:" + advData.getStopDepTime().toString());
            } else if (advData.getStopArrTime() != null) {
                parts.add("TTA:" + advData.getStopDepTime().toString());
            }
            if (advData.getStopCisNumber() != 0) {
                parts.add("S:" + advData.getStopCisNumber());
            }
            parts.add("LS:" + convertLocationStateFromAdv(advData.getLocationState()));
        }
        return String.join("|", parts);
    }

    private byte[] createEtd(UUID sessionId, OffsetDateTime validFrom, Duration ttl) {
        List<String> parts = new ArrayList<>();
        parts.add("IN:DPP");
        parts.add("VS:" + validFrom);
        parts.add("VU:" + validFrom.plus(ttl));
        parts.add("X-SID:" + sessionId.toString());
        parts.add("X-LWT:" + createLwtMetadata());
        return ("ETD*1*" + String.join("*", parts) + "*").getBytes(StandardCharsets.UTF_8);
    }

    private void pushTicketEvent(int type, UUID sessionId, UUID eventId, UUID lastEventId) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(out)) {
            dos.writeLong(1); // mock account ID
            dos.write(BLEScanRecordUtil.uuidToBytes(sessionId));
            dos.write(BLEScanRecordUtil.uuidToBytes(eventId));
            if (lastEventId != null) {
                dos.write(BLEScanRecordUtil.uuidToBytes(lastEventId));
            } else {
                dos.write(new byte[16]);
            }
            OffsetDateTime time = OffsetDateTime.now();
            dos.writeLong(time.toInstant().toEpochMilli());
            dos.writeInt(time.getOffset().getTotalSeconds());
            dos.writeUTF(createLwtMetadata());

            mockServerClient.sendClientEvent(type, out.toByteArray());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void pushTrackPresenceEvent(byte[] trackingDeviceAddress, UUID sessionId) {
        ByteBuffer out = ByteBuffer.allocate(trackingDeviceAddress.length + 16);
        out.put(trackingDeviceAddress);
        out.put(BLEScanRecordUtil.uuidToBytes(sessionId));
        mockServerClient.sendClientEvent(DEVICE_EVENT_TRACK_PRESENCE, out.array());
    }

    private byte[] createTicketRefreshToken(UUID sessionId, UUID eventId) {
        ByteBuffer out = ByteBuffer.allocate(2 * 16);
        out.put(BLEScanRecordUtil.uuidToBytes(sessionId));
        out.put(BLEScanRecordUtil.uuidToBytes(eventId));
        return out.array();
    }

    private CICOTicketFragment createMockTicket(UUID sessionId, UUID eventId) {
        FlatBufferBuilder fbb = new FlatBufferBuilder();
        OffsetDateTime time = OffsetDateTime.now();
        Duration ttl = mockServerClient.translateDuration(Duration.ofMinutes(5));
        int issuerAddresses = CICOTicketFragment.createIssuerAddressesVector(fbb, new int[0]);
        int issuerCertificate = CICOTicketFragment.createIssuerCertificateVector(fbb, new byte[0]);
        int etd = CICOTicketFragment.createEtdVector(fbb, createEtd(sessionId, time, ttl));
        int totpSeed = CICOTicketFragment.createTotpSeedVector(fbb, new byte[32]);
        int refreshToken = CICOTicketFragment.createRefreshTokenVector(fbb, createTicketRefreshToken(sessionId, eventId));
        CICOTicketFragment.startCICOTicketFragment(fbb);
        CICOTicketFragment.addIssuerAddresses(fbb, issuerAddresses);
        CICOTicketFragment.addIssuerCertificate(fbb, issuerCertificate);
        CICOTicketFragment.addEtd(fbb, etd);
        CICOTicketFragment.addTotpSeed(fbb, totpSeed);
        CICOTicketFragment.addRefreshToken(fbb, refreshToken);
        CICOTicketFragment.addIssuedAt(fbb, time.toInstant().toEpochMilli());
        CICOTicketFragment.addIssuedAtAbsolute(fbb, LwtTime.createOffsetDateTime(fbb, time));
        CICOTicketFragment.addTtl(fbb, ttl.toMillis());
        fbb.finish(CICOTicketFragment.endCICOTicketFragment(fbb));
        return repackFbb(fbb, CICOTicketFragment.class);
    }

    private CompletableFuture<CICOTicketFragment> doTicketRefresh(CICOTicketFragment lastTicket, UUID sessionId) {
        return futureAfterSuccessfulConnect(() -> {
            UUID eventId = UUID.randomUUID();
            CICOTicketFragment newTicket = createMockTicket(sessionId, eventId);
            if (lastTicket == null) {
                pushTicketEvent(DEVICE_EVENT_CHECK_IN, sessionId, eventId, null);
            } else {
                pushTicketEvent(DEVICE_EVENT_REFRESH, sessionId, eventId, getLastEventIdFromTicket(lastTicket));
            }
            pushTrackPresenceEvent(Objects.requireNonNull(device.getAddress().getRawLinkAddress()), sessionId);
            return newTicket;
        });
    }

    @Override
    public CompletableFuture<CICOTicketFragment> confirmCheckIn(CheckInIntermediate intermediate, PresenceTrackingClient presenceTrackingClient) {
        // session id is extracted from confirmation token
        return doTicketRefresh(null, BLEScanRecordUtil.parseUUID128(intermediate.confirmationTokenAsByteBuffer()));
    }

    private UUID getSessionIdFromTicket(CICOTicketFragment ticket) {
        return BLEScanRecordUtil.parseUUID128(ByteBufferUtils.toByteArray(ticket.refreshTokenAsByteBuffer(), 0, 16));
    }

    private UUID getLastEventIdFromTicket(CICOTicketFragment ticket) {
        return BLEScanRecordUtil.parseUUID128(ByteBufferUtils.toByteArray(ticket.refreshTokenAsByteBuffer(), 16, 16));
    }

    @Override
    public CompletableFuture<CICOTicketFragment> refreshTicket(CICOTicketFragment lastTicket, PresenceTrackingClient presenceTrackingClient) {
        return doTicketRefresh(lastTicket, getSessionIdFromTicket(lastTicket));
    }

    @Override
    public CompletableFuture<CheckOutResponse> checkOut(CICOTicketFragment currentTicket) {
        return futureAfterSuccessfulConnect(() -> {
            UUID sessionId = getSessionIdFromTicket(currentTicket);
            pushTrackPresenceEvent(PRESENCE_TRACKING_OFF_ADDRESS, sessionId);
            pushTicketEvent(DEVICE_EVENT_CHECK_OUT, sessionId, UUID.randomUUID(), getLastEventIdFromTicket(currentTicket));
            FlatBufferBuilder fbb = new FlatBufferBuilder();
            fbb.finish(CheckOutResponse.createCheckOutResponse(fbb, CheckOutResponse.createSessionIdVector(fbb, BLEScanRecordUtil.uuidToBytes(sessionId))));
            return repackFbb(fbb, CheckOutResponse.class);
        });
    }

    @Override
    public void close() {
        // do not close the mocking server client, as it is used for device scans too
    }
}
