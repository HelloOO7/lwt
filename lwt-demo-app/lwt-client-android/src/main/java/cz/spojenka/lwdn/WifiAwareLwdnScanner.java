package cz.spojenka.lwdn;

import android.net.wifi.ScanResult;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.AwareResources;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.ServiceDiscoveryInfo;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public class WifiAwareLwdnScanner implements LwdnScanner {

    private static final String TAG = "WifiAwareLwdnScanner";

    private final WifiAwareManager awareManager;
    private final WifiAwareSessionManager sessionManager;
    private final int servicePort;

    public WifiAwareLwdnScanner(WifiAwareManager awareManager, WifiAwareSessionManager sessionManager, int servicePort) {
        this.awareManager = awareManager;
        this.sessionManager = sessionManager;
        this.servicePort = servicePort;
    }

    @Override
    public boolean isAvailable() {
        return awareManager.isAvailable();
    }

    @Override
    public boolean isUsingExtendedAdvertising() {
        return true;
    }

    @Override
    public LwdnScan startScan(List<LwdnServiceID> services, LwdnScanConfig config) {
        LwdnScan scan = new LwdnScan();

        new ScanController(scan, sessionManager, servicePort).startScan(services, config);

        return scan;
    }

    private static class ScanController {

        private final LwdnScan scan;
        private final WifiAwareSessionManager sessionManager;
        private final int servicePort;

        private AttachCallback currentAttachCallback;
        private final List<SubscribeDiscoverySession> currentDiscoverySessions = new ArrayList<>();

        public ScanController(LwdnScan scan, WifiAwareSessionManager sessionManager, int servicePort) {
            this.scan = scan;
            this.sessionManager = sessionManager;
            this.servicePort = servicePort;

            scan.setCancellationHandler(this::cleanUpAndMarkFinished);
        }

        private void cleanUpAndMarkFinished() {
            cleanUp();
            scan.markFinished();
        }

        private void cleanUp() {
            Log.d(TAG, "Requested clean-up of Wi-Fi Aware scan resources");
            for (SubscribeDiscoverySession session : currentDiscoverySessions) {
                Log.d(TAG, "Closing discovery session: " + session);
                session.close();
            }
            if (currentAttachCallback != null) {
                sessionManager.detach(currentAttachCallback);
                currentAttachCallback = null;
            }
        }

        private Map<LwdnServiceID.ServiceName, List<LwdnServiceID.ServiceName>> unifyServicesWithSameName(List<LwdnServiceID> services) {
            Map<String, List<LwdnServiceID.ServiceName>> servicesByName = new HashMap<>();
            for (LwdnServiceID serviceId : services) {
                if (serviceId instanceof LwdnServiceID.ServiceName serviceName) {
                    servicesByName.computeIfAbsent(serviceName.name(), k -> new ArrayList<>()).add(serviceName);
                }
            }
            Map<LwdnServiceID.ServiceName, List<LwdnServiceID.ServiceName>> unifiedServices = new HashMap<>();
            for (Map.Entry<String, List<LwdnServiceID.ServiceName>> entry : servicesByName.entrySet()) {
                List<LwdnServiceID.MatchingFilterSlot> allMatchingFilters = new ArrayList<>();
                for (LwdnServiceID.ServiceName serviceId : entry.getValue()) {
                    allMatchingFilters.addAll(serviceId.matchingFilters());
                }
                unifiedServices.put(new LwdnServiceID.ServiceName(entry.getKey(), allMatchingFilters), entry.getValue());
            }
            return unifiedServices;
        }

        public void startScan(List<LwdnServiceID> services, LwdnScanConfig config) {
            var unifiedNames = unifyServicesWithSameName(services);

            currentAttachCallback = new AttachCallback() {

                @Override
                public void onAttached(WifiAwareSession session) {
                    Log.d(TAG, "Attached to Wi-Fi Aware session: " + session);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AwareResources resources = sessionManager.getAwareManager().getAvailableAwareResources();
                        if (resources != null) {
                            Log.d(TAG, "Aware resources: pubSessions=" + resources.getAvailablePublishSessionsCount()
                                    + ", subSessions=" + resources.getAvailableSubscribeSessionsCount()
                                    + ", NDPs=" + resources.getAvailableDataPathsCount());
                        }
                    }
                    for (var serviceCollection : unifiedNames.entrySet()) {
                        LwdnServiceID.ServiceName serviceName = serviceCollection.getKey();
                        try {
                            session.subscribe(createSubscribeConfig(serviceName, config), new DiscoverySessionCallback() {

                                private SubscribeDiscoverySession mySession;

                                @Override
                                public void onSubscribeStarted(@NonNull SubscribeDiscoverySession discoverySession) {
                                    Log.d(TAG, "Subscribe started for service: " + serviceName + ", session: " + discoverySession);
                                    if (scan.isFinished()) {
                                        Log.d(TAG, "Scan already finished, opened discovery session will be closed immediately");
                                        discoverySession.close();
                                    } else {
                                        mySession = discoverySession;
                                        currentDiscoverySessions.add(discoverySession);
                                    }
                                }

                                private LwdnServiceID.ServiceName findActualServiceName(List<byte[]> matchedFilters) {
                                    for (var serviceId : serviceCollection.getValue()) {
                                        if (serviceId.checkFilterMatched(matchedFilters)) {
                                            return serviceId;
                                        }
                                    }
                                    return serviceCollection.getKey();
                                }

                                @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
                                @Override
                                public void onServiceDiscovered(@NonNull ServiceDiscoveryInfo info) {
                                    handlePublisherFound(info.getPeerHandle(), findActualServiceName(info.getMatchFilters()), info.getServiceSpecificInfo(), 0);
                                }

                                @Override
                                public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter) {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        handlePublisherFound(peerHandle, findActualServiceName(matchFilter), serviceSpecificInfo, 0);
                                    }
                                }

                                @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
                                @Override
                                public void onServiceDiscoveredWithinRange(@NonNull ServiceDiscoveryInfo info, int distanceMm) {
                                    handlePublisherFound(info.getPeerHandle(), findActualServiceName(info.getMatchFilters()), info.getServiceSpecificInfo(), -distanceMm);
                                }

                                @Override
                                public void onServiceDiscoveredWithinRange(PeerHandle peerHandle, byte[] serviceSpecificInfo, List<byte[]> matchFilter, int distanceMm) {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        handlePublisherFound(peerHandle, findActualServiceName(matchFilter), serviceSpecificInfo, -distanceMm);
                                    }
                                }

                                private final Map<PeerHandle, WifiAwareLwdnAddress> knownPeers = new HashMap<>();

                                @Override
                                public void onServiceLost(@NonNull PeerHandle peerHandle, int reason) {
                                    WifiAwareLwdnAddress address = knownPeers.get(peerHandle);
                                    if (address != null) {
                                        Log.d(TAG, "Service lost for peer: " + peerHandle + ", address: " + address + ", reason: " + reason);
                                        scan.removeResult(new LwdnScanResult(address, 0, Map.of()));
                                        knownPeers.remove(peerHandle);
                                    }
                                }

                                private final Map<PeerHandle, PendingPeerInfo> pendingPeers = new HashMap<>();

                                private void handlePublisherFound(PeerHandle peer, LwdnServiceID serviceID, byte[] ssi, int rssi) {
                                    Log.d(TAG, "Discovered publisher: peer=" + peer + ", serviceID=" + serviceID + ", ssi=" + Arrays.toString(ssi) + ", rssi=" + rssi);
                                    if (ssi != null && ssi.length > 0 && (ssi.length > 1 || ssi[0] != -1)) {
                                        handleScanResult(peer, serviceID, ssi, rssi);
                                    } else {
                                        pendingPeers.put(peer, new PendingPeerInfo(rssi, serviceID));
                                        requestDynamicSsi(peer);
                                    }
                                }

                                private void requestDynamicSsi(PeerHandle peer) {
                                    // message can not be empty, otherwise android does not send it
                                    mySession.sendMessage(peer, 0, new byte[]{-1});
                                }

                                @Override
                                public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
                                    PendingPeerInfo pendingInfo = pendingPeers.get(peerHandle);
                                    if (pendingInfo == null) {
                                        Log.w(TAG, "Received message from unknown peer: " + peerHandle);
                                        return;
                                    }
                                    Log.d(TAG, "Follow-up received from " + peerHandle);
                                    handleScanResult(peerHandle, pendingInfo.serviceID(), message, pendingInfo.rssi());
                                    pendingPeers.remove(peerHandle);
                                }

                                private void handleScanResult(PeerHandle peerHandle, LwdnServiceID serviceID, byte[] serviceSpecificInfo, int rssi) {
                                    if (scan.isFinished()) {
                                        return;
                                    }
                                    if (serviceSpecificInfo.length < 6) {
                                        return;
                                    }
                                    if (scan.getResultCount() < config.getMaxDevices()) {
                                        byte[] peerMacAddress = Arrays.copyOfRange(serviceSpecificInfo, 0, 6);
                                        byte[] innerSsi = Arrays.copyOfRange(serviceSpecificInfo, peerMacAddress.length, serviceSpecificInfo.length);
                                        WifiAwareLwdnAddress finalAddress = WifiAwareLwdnAddress.create(mySession, peerHandle, servicePort).withKnownAddress(peerMacAddress);
                                        Log.d(TAG, "Found LWDN device: " + finalAddress + ", rssi=" + rssi + ", serviceID=" + serviceID);
                                        knownPeers.put(peerHandle, finalAddress);
                                        scan.addResult(new LwdnScanResult(
                                                finalAddress,
                                                rssi,
                                                Map.of(serviceID, innerSsi)
                                        ));
                                    }
                                    if (scan.getResultCount() >= config.getMaxDevices()) {
                                        Log.d(TAG, "Reached max results limit, finishing scan");
                                        cleanUpAndMarkFinished();
                                    }
                                }

                                @Override
                                public void onMessageSendSucceeded(int messageId) {
                                    Log.d(TAG, "Message sent successfully: " + messageId);
                                }

                                @Override
                                public void onMessageSendFailed(int messageId) {
                                    Log.d(TAG, "Message send failed: " + messageId);
                                }

                                @Override
                                public void onSessionTerminated() {
                                    Log.d(TAG, "Discovery session " + this + " terminated");
                                    WifiAwareLwdnAddress.onSessionTerminated(mySession);
                                    currentDiscoverySessions.remove(mySession);
                                    mySession = null;
                                }
                            }, sessionManager.getCallbackHandler());
                        } catch (SecurityException ex) {
                            scan.markFailed(new LwdnScanException(ScanErrorCode.NOT_PERMITTED, "Missing required permissions for Wi-Fi Aware subscription", ex));
                        }
                    }
                }

                @Override
                public void onAttachFailed() {
                    scan.markFailed(new LwdnScanException(ScanErrorCode.OUT_OF_RESOURCES, "Failed to attach to Wi-Fi Aware session"));
                }
            };
            sessionManager.attach(currentAttachCallback);

            if (config.getTimeout() != null) {
                sessionManager.getCallbackHandler().postDelayed(this::cleanUpAndMarkFinished, config.getTimeout().toMillis());
            }
        }

        @SuppressWarnings("deprecation")
        private SubscribeConfig createSubscribeConfig(LwdnServiceID.ServiceName serviceName, LwdnScanConfig config) {
            SubscribeConfig.Builder builder = new SubscribeConfig.Builder()
                    .setServiceName(serviceName.name())
                    .setMatchFilter(serviceName.compileMatchingFilters())
                    .setSubscribeType(SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE)
                    .setTerminateNotificationEnabled(true);

            if (config.getScanMode() == LwdnScanConfig.ScanMode.LOW_LATENCY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Characteristics chars = sessionManager.getAwareManager().getCharacteristics();
                if (chars != null && chars.isInstantCommunicationModeSupported()) {
                    builder.setInstantCommunicationModeEnabled(true, ScanResult.WIFI_BAND_5_GHZ);
                }
            }

            if (config.hasMaxDistance()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                    builder.setIngressDistanceMm(config.getMaxDistanceMm());
                } else {
                    builder.setMaxDistanceMm(config.getMaxDistanceMm());
                }
            }

            return builder.build();
        }
    }

    private static record PendingPeerInfo(int rssi, LwdnServiceID serviceID) {

    }
}
