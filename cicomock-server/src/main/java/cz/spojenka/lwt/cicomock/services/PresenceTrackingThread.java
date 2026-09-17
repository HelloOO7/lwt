package cz.spojenka.lwt.cicomock.services;

import cz.spojenka.lwt.cicomock.controllers.ClientWSHandler;
import cz.spojenka.lwt.cicomock.model.Location;
import cz.spojenka.lwt.cicomock.model.MockState;
import cz.spojenka.lwt.cicomock.model.NearbyDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PresenceTrackingThread extends Thread implements MockState.Observer {

    private final Logger logger = LoggerFactory.getLogger(PresenceTrackingThread.class);

    @Value("${mock.presence.update-interval}")
    private long updateInterval;
    @Value("${mock.presence.loss-interval}")
    private long lossInterval;
    @Value("${mock.presence.min-present-rssi}")
    private int minPresentRssi;

    private final MockState state;
    private byte[] trackingDevAddr;
    private UUID sessionId;
    private ClientWSHandler parent;
    private boolean shouldEnd = false;
    private String lastDevMetadata;

    public PresenceTrackingThread(MockState state) {
        this.state = state;
    }

    public void configure(UUID sessionId, ClientWSHandler parent) {
        this.sessionId = sessionId;
        this.parent = parent;
    }

    public synchronized void changeTrackingDevice(byte[] trackingDevAddr) {
        if (Arrays.equals(this.trackingDevAddr, trackingDevAddr)) {
            return;
        }
        logger.info("Presence tracking device changed to {}", Arrays.toString(trackingDevAddr));
        this.trackingDevAddr = trackingDevAddr;
        state.setPresenceTrackingDevice(trackingDevAddr);
    }

    @Override
    public void run() {
        state.addObserver(this);
        try {
            long lastDisconnected = 0;
            while (!shouldEnd) {
                state.getTimekeeper().sleepCurrentThread(Duration.ofMillis(updateInterval), () -> shouldEnd);
                if (shouldEnd) {
                    break;
                }
                synchronized (this) {
                    if (trackingDevAddr != null) {
                        if (isDisconnected()) {
                            long scaledLossInterval = (long) (lossInterval / state.getTimescale());
                            if (lastDisconnected == 0) {
                                logger.info("Device moved out of range, will lose presence in {} ms", scaledLossInterval);
                                lastDisconnected = System.currentTimeMillis();
                            } else if (System.currentTimeMillis() - lastDisconnected > scaledLossInterval) {
                                trackingDevAddr = null;
                                lastDisconnected = 0;
                                if (lastDevMetadata != null) {
                                    logger.info("Presence tracking lost for session {}, last metadata: {}", sessionId, lastDevMetadata);
                                    parent.onPresenceTrackingLost(sessionId, lastDevMetadata);
                                    state.setPresenceTrackingConnectionId(0);
                                } else {
                                    logger.warn("Presence tracking lost for session {}, but no LWT metadata available", sessionId);
                                }
                            }
                        } else {
                            lastDisconnected = 0;
                        }
                    }
                }
            }
            if (trackingDevAddr != null) {
                logger.info("Session {} aborted while presence tracking was active sending BE_OUT with: {}", sessionId, lastDevMetadata);
                parent.onPresenceTrackingLost(sessionId, lastDevMetadata);
                state.setPresenceTrackingConnectionId(0);
            }
        } finally {
            state.removeObserver(this);
        }
        logger.info("Presence tracking thread for session {} ended", sessionId);
    }

    private boolean isDisconnected() {
        Location loc = state.getLocation();
        for (var dev : state.getNearbyDevices()) {
            if (Arrays.equals(dev.address(), trackingDevAddr)) {
                if (AdvDataConverter.computeDeviceRssi(dev, loc) >= minPresentRssi) {
                    return false;
                }
            }
        }
        return true;
    }

    public synchronized void end() {
        shouldEnd = true;
        interrupt();
    }

    @Override
    public void onNearbyDevicesChanged(List<NearbyDevice> nearbyDevices) {
        for (NearbyDevice dev : nearbyDevices) {
            if (Arrays.equals(dev.address(), trackingDevAddr)) {
                synchronized (this) {
                    lastDevMetadata = AdvDataConverter.createLwtMetadataFromAdvData(dev.advData());
                }
                break;
            }
        }
    }
}
