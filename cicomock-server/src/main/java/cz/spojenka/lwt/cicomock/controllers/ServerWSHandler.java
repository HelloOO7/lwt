package cz.spojenka.lwt.cicomock.controllers;

import cz.spojenka.lwt.cicomock.api.map.MapChangeRequest;
import cz.spojenka.lwt.cicomock.api.map.MapDataPush;
import cz.spojenka.lwt.cicomock.model.Location;
import cz.spojenka.lwt.cicomock.model.MockState;
import cz.spojenka.lwt.cicomock.model.NearbyDevice;
import cz.spojenka.lwt.cicomock.services.AdvDataConverter;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ServerWSHandler extends TextWebSocketHandler implements MockState.Observer {

    private final MockState state;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    @Value("${mock.scan.min-report-rssi}")
    private int minReportRssi;
    @Value("${mock.presence.min-present-rssi}")
    private int minPresentRssi;

    private final List<WebSocketSession> currentSessions = new ArrayList<>();

    public ServerWSHandler(MockState state) {
        this.state = state;

        state.addObserver(this);
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        currentSessions.add(session);
        broadcastMessage(session, createMapData());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        currentSessions.remove(session);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) throws Exception {
        MapChangeRequest request = jsonMapper.readValue(message.getPayload(), MapChangeRequest.class);
        if (request.newTimescale() != null) {
            state.setTimescale(request.newTimescale());
        }
        if (request.newTime() != null) {
            state.setCurrentTime(request.newTime());
        }
        if (request.newLocation() != null) {
            state.updateLocation(request.newLocation());
        }
        if (request.newTrackingConnection() != null) {
            int trackingConnection = request.newTrackingConnection();
            if (trackingConnection <= 0) {
                state.setTrackingConnectionId(0);
            } else {
                state.setTrackingConnectionId(trackingConnection);
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        updateMapData();
    }

    @Override
    public void onNearbyDevicesChanged(List<NearbyDevice> nearbyDevices) {
        updateMapData();
    }

    @Override
    public void onTimescaleChanged(float timescale) {
        updateMapData();
    }

    @Override
    public void onPresenceTrackingChanged(int presenceTrackingConnectionId) {
        updateMapData();
    }

    private MapDataPush createMapData() {
        return new MapDataPush(
                state.getLocation(),
                state.getTrackingConnectionId(),
                state.getCurrentTime(),
                state.getTimescale(),
                state.getNearbyDevices(),
                state.getPresenceTrackingConnectionId(),
                (float) AdvDataConverter.estimateRadiusMetersForRssi(minReportRssi),
                (float) AdvDataConverter.estimateRadiusMetersForRssi(minPresentRssi)
        );
    }

    private void updateMapData() {
        try {
            broadcastMessage(null, createMapData());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void broadcastMessage(WebSocketSession session, Object object) throws IOException {
        String data = jsonMapper.writeValueAsString(object);
        broadcastMessage(session, data);
    }

    private synchronized void broadcastMessage(WebSocketSession session, String data) throws IOException {
        TextMessage message = new TextMessage(data);
        if (session != null) {
            session.sendMessage(message);
        } else {
            for (WebSocketSession session2 : currentSessions) {
                session2.sendMessage(message);
            }
        }
    }
}
