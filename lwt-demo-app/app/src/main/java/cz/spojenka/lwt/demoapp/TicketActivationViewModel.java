package cz.spojenka.lwt.demoapp;

import android.app.Application;
import android.util.Log;

import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import cz.spojenka.android.system.TickNotifier;
import cz.spojenka.android.system.livedata.LiveList;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.android.util.LiveDataUtils;
import cz.spojenka.lwdn.LwdnScanException;
import cz.spojenka.lwt.CommType;
import cz.spojenka.lwt.LwtAPIClient;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.LwtDeviceType;
import cz.spojenka.lwt.PreauthorizationToken;
import cz.spojenka.lwt.PreauthorizationTokenResult;
import cz.spojenka.lwt.PreauthorizationTokenStatus;
import cz.spojenka.lwt.StopReference;
import cz.spojenka.lwt.TicketActivationParams;
import cz.spojenka.lwt.TicketActivationResponse;
import cz.spojenka.lwt.TicketValidationInfo;
import cz.spojenka.lwt.TokenWithExpiration;
import cz.spojenka.lwt.TripRouteInfo;
import cz.spojenka.lwt.TripStopInfo;
import cz.spojenka.lwt.util.ByteBufferUtils;
import cz.spojenka.lwt.util.LwtTariffZones;
import cz.spojenka.lwt.util.LwtTime;
import cz.spojenka.lwtp.LwtpLoggingObserver;
import cz.spojenka.lwtp.LwtpTLSConfig;
import cz.spojenka.lwtp.LwtpTLSPolicy;

public class TicketActivationViewModel extends AndroidViewModel {

    private static final String TAG = "TicketActivation";

    private static final boolean MOCK_UNTRUSTED_SERVER = false;
    private static final boolean MOCK_PREAUTH_ALWAYS_OK = false;

    private TicketData ticket;

    private final DeviceListViewModel devicesViewModel;

    // we are broadcasting over a wireless radio channel to a single-threaded server, so there
    // is no point in doing so asynchronously and, in fact, it could needlessly overload the server,
    // so we create a single thread for doing it
    private final Executor lwtRequestThread = Executors.newSingleThreadExecutor();

    private LwtAPIClient lwtClient;
    private LwtAPIClient secureLwtClient;

    private MutableLiveData<LwtDevice> selectedAutoActivationDevice = new MutableLiveData<>();
    private List<CompletableFuture<Void>> currentDeviceDataRequests = new ArrayList<>();
    private MutableLiveData<Boolean> deviceDataIsLoading = new MutableLiveData<>(false);
    private MutableLiveData<TripRouteInfo> deviceRouteInfo = new MutableLiveData<>();
    private MutableLiveData<TicketValidationInfo> deviceValidationInfo = new MutableLiveData<>();
    private MutableLiveData<Boolean> rawServerAuthenticationResult = new MutableLiveData<>();
    private MutableLiveData<TokenWithExpiration<PreauthorizationTokenResult>> devicePreauthToken = new MutableLiveData<>();
    private MutableLiveData<Long> preauthExpirationSecondsLeft = new MutableLiveData<>(null);

    private MutableLiveData<Throwable> autoDownloadException = new MutableLiveData<>();

    private List<String> prepaidZones = List.of();
    private List<String> lastChosenZones = List.of();
    private MutableLiveData<ZoneChoice> chosenZones = new MutableLiveData<>();
    private boolean canNotUseTicketWithDevice = false;

    private LocalDateTime defaultActivationTimeForTviStop;

    private ActivationTime currentActivationTime;
    private MutableLiveData<ActivationTime> activationTime = new MutableLiveData<>();

    private StopReference tviActivationStop;
    private StopReference defaultActivationStop;
    private boolean forceUseTviStop = false;

    private StopReference currentActivationStop;
    private MutableLiveData<StopReference> activationStop = new MutableLiveData<>();
    private MutableLiveData<StopReference> validityEndStop = new MutableLiveData<>();
    private List<String> activationStopTicketZones = List.of();

    private CompletableFuture<?> currentActivationCall;
    private MutableLiveData<Throwable> activationError = new MutableLiveData<>();
    private MutableLiveData<TicketActivationResponse> activationResult = new MutableLiveData<>();
    private MutableLiveData<Boolean> isActivationInProgress = new MutableLiveData<>(false);

    public TicketActivationViewModel(@NonNull Application application) {
        super(application);
        devicesViewModel = new DeviceListViewModel(application);

        devicesViewModel.setDeviceTypes(List.of(LwtDeviceType.VEHICLE));
        devicesViewModel.setUseContinuousScan(true);
        devicesViewModel.startScan();

        setChosenZonesAuto();
        setActivationTimeNow(false);

        TickNotifier tickNotifier = new TickNotifier(getApplication(), this::onTimeTick, 1000);
        tickNotifier.register();

        addCloseable(devicesViewModel::close);
        addCloseable(tickNotifier::unregister);
    }

    public void setTicket(TicketData ticket) {
        this.ticket = ticket;
    }

    public void setPrepaidZones(List<String> prepaidZones) {
        this.prepaidZones = prepaidZones;
    }

    public DeviceListViewModel getDevicesViewModel() {
        return devicesViewModel;
    }

    public LiveList<LwtDevice> getAutoActivationDeviceOptions() {
        return devicesViewModel.getDeviceResults();
    }

    public LiveData<LwtDevice> getSelectedAutoActivationDevice() {
        return selectedAutoActivationDevice;
    }

    public LiveData<LwdnScanException> getAutoScanException() {
        return devicesViewModel.getScanError();
    }

    public void discardAutoActivationDeviceFull() {
        clearAutoActivationDevice(true);
    }

    private void clearAutoActivationDevice(boolean full) {
        currentDeviceDataRequests.forEach(r -> r.cancel(true));
        currentDeviceDataRequests = List.of();

        currentActivationStop = null;
        tviActivationStop = null;
        defaultActivationStop = null;
        defaultActivationTimeForTviStop = null;
        forceUseTviStop = false;
        canNotUseTicketWithDevice = false;
        activationStopTicketZones = List.of();

        selectedAutoActivationDevice.setValue(null);
        autoDownloadException.setValue(null);
        deviceValidationInfo.setValue(null);
        deviceRouteInfo.setValue(null);
        rawServerAuthenticationResult.setValue(null);
        activationStop.setValue(null);
        devicePreauthToken.setValue(null);
        preauthExpirationSecondsLeft.setValue(null);

        var chosenZones = getChosenZones().getValue();
        if (chosenZones != null && !chosenZones.isManual() && !chosenZones.zones().isEmpty()) {
            if (full) {
                setChosenZones(List.of(), false);
            }
        }
        var time = getActivationTime().getValue();
        if (time != null && !time.isManual()) {
            if (time.isNow()) {
                setActivationTimeNow(false);
            } else {
                if (full) {
                    setActivationTimeAuto();
                } else {
                    setActivationTime(time.time(), false);
                }
            }
        }
    }

    public void selectAutoActivationDevice(LwtDevice device) {
        clearAutoActivationDevice(false);
        if (device == null) {
            return;
        }

        selectedAutoActivationDevice.setValue(device);

        deviceDataIsLoading.setValue(true);

        lwtClient = new LwtAPIClient(device.getAddress());
        lwtClient.disableTLS(); // at this stage, use unencrypted connection
        //lwtClient.addSessionExecutionObserver(new LwtpLoggingObserver());
        secureLwtClient = new LwtAPIClient(device.getAddress());
        try {
            SSLContext sslContext;
            if (MOCK_UNTRUSTED_SERVER) {
                // default SSL context will not trust our self-signed certs
                sslContext = SSLContext.getDefault();
            } else {
                sslContext = GlobalTrustManager.getInstance(getApplication()).createSSLContext();
            }

            secureLwtClient.useTLS(
                    new LwtpTLSConfig.Builder(device.getAddress())
                            .setTLSPolicy(LwtpTLSPolicy.EXPLICIT_REQUIRED)
                            .setSSLContext(sslContext)
                            .build()
            );
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "Failed to create SSL context for secure LWT client, fallback to insecure", e);
            secureLwtClient = lwtClient;
        }

        List<CompletableFuture<?>> requestsForLoadingIndicator = new ArrayList<>();

        requestsForLoadingIndicator.add(enqueueDataDownloadRequest(lwtClient::getTicketValidationInfo, deviceValidationInfo).thenAcceptAsync(tvi -> {
            defaultActivationTimeForTviStop = LwtTime.convertLocalDateTime(tvi.scheduledActivationTime());
            tviActivationStop = tvi.trip().currentDepartureStop();
            forceUseTviStop |= isShouldUseTicketValidationStop(tvi);
            canNotUseTicketWithDevice = checkAllDeviceZonesNotApplicable(tvi);
            commitDefaultActivationStopIfAllData();
        }, getApplication().getMainExecutor()));
        requestsForLoadingIndicator.add(enqueueDataDownloadRequest(lwtClient::getTripRouteInfo, deviceRouteInfo).thenAcceptAsync(tri -> {
            if (!forceUseTviStop) {
                // tvi may overwrite this if it arrives later
                defaultActivationStop = calcDefaultActivationStopWithPrepaid(tri);
            }
            commitDefaultActivationStopIfAllData();
        }, getApplication().getMainExecutor()));

        enqueueDataDownloadRequest((commType) -> secureLwtClient.requestPreauthorizationToken(ticket.getActivationToken(), commType), devicePreauthToken)
                .thenAcceptAsync(token -> updatePreauthExpirationTime(), getApplication().getMainExecutor());

        Log.d(TAG, "Starting async data download from device " + device.getAddress());
        CompletableFuture<Void> dataDownloadFuture = lwtClient.executeAsync(lwtRequestThread);
        CompletableFuture<Void> secureDataDownloadFuture = secureLwtClient.executeAsync(lwtRequestThread);

        dataDownloadFuture.whenCompleteAsync((unused, throwable) -> {
            Log.d(TAG, "Cleartext data download completed.");
        }, getApplication().getMainExecutor());
        secureDataDownloadFuture.whenCompleteAsync((unused, throwable) -> {
            Log.d(TAG, "Secure data download completed.");
            if (AsyncUtils.unwrapCompletionException(throwable) instanceof SSLException) {
                rawServerAuthenticationResult.setValue(false);
            } else {
                rawServerAuthenticationResult.setValue(true);
            }
        }, getApplication().getMainExecutor());

        CompletableFuture<Void> loadingOffFuture = CompletableFuture
                .allOf(requestsForLoadingIndicator.toArray(new CompletableFuture[0]))
                .whenCompleteAsync((unused, throwable) -> {
                    deviceDataIsLoading.setValue(false);
                }, getApplication().getMainExecutor());

        currentDeviceDataRequests = List.of(dataDownloadFuture, secureDataDownloadFuture, loadingOffFuture);
    }

    private void commitDefaultActivationStopIfAllData() {
        if (deviceRouteInfo.getValue() != null && tviActivationStop != null) {
            defaultActivationStop = (defaultActivationStop == null || forceUseTviStop) ? tviActivationStop : defaultActivationStop;
            setActivationStop(defaultActivationStop);
        }
    }

    private boolean checkAllDeviceZonesNotApplicable(TicketValidationInfo tvi) {
        if (getZoneOptions() == null) {
            return false;
        }

        Set<String> zones = new HashSet<>();
        zones.addAll(LwtTariffZones.findEntryForTariffSystem(tvi.tariffZones(), getTariffSystemId()).zones());
        zones.addAll(LwtTariffZones.findEntryForTariffSystem(tvi.nextTariffZones(), getTariffSystemId()).zones());
        zones.retainAll(Set.copyOf(getZoneOptions()));

        return zones.isEmpty();
    }

    public String getTariffSystemId() {
        return ticket.getTariffSystemId();
    }

    private boolean isPID() {
        return "PID".equals(getTariffSystemId());
    }

    private boolean isShouldUseTicketValidationStop(TicketValidationInfo tvi) {
        var zones = LwtTariffZones.findEntryForTariffSystem(tvi.tariffZones(), getTariffSystemId());
        boolean hasValidZone = false;
        if (zones != null) {
            for (String idsZone : zones.zones()) {
                if (isPID() && !isPIDZone(idsZone)) {
                    continue;
                }
                if (prepaidZones.contains(idsZone)) {
                    return false;
                } else {
                    hasValidZone = true;
                }
            }
        }
        return hasValidZone;
    }

    private StopReference calcDefaultActivationStopWithPrepaid(TripRouteInfo tri) {
        Set<String> prepaid = Set.copyOf(prepaidZones);
        int firstStop = tri.trip().currentDepartureStop().sequenceId();
        for (int i = firstStop; i < tri.stopsLength(); i++) {
            TripStopInfo stop = tri.stops(i);
            List<String> stopZones = getStopZonesOrEmpty(stop);
            if (hasPrepaidZones(stopZones, prepaid)) {
                // retain only prepaid, for calcPassthroughZones
                stopZones = new ArrayList<>(stopZones);
                stopZones.retainAll(prepaid);
            }
            if (stopZones.isEmpty()) {
                continue;
            }
            List<String> passthroughZones = List.of();
            if (i + 1 < tri.stopsLength()) {
                TripStopInfo nextStop = tri.stops(i + 1);
                passthroughZones = calcPassthroughZones(stopZones, getStopZonesOrEmpty(nextStop), true);
            }
            boolean mustActivateHere = !hasPrepaidZones(stopZones, prepaid) || hasNonPrepaidZone(passthroughZones, prepaid);
            if (mustActivateHere) {
                return stop.stopRef();
            }
        }
        return null;
    }

    private List<String> calcPassthroughZones(TripStopInfo from, TripStopInfo to, boolean includeLast) {
        if (!isPID()) {
            return List.of();
        }
        List<String> fromZones = getStopZonesOrEmpty(from);
        List<String> toZones = getStopZonesOrEmpty(to);
        return calcPassthroughZones(fromZones, toZones, includeLast);
    }

    /**
     * Calculate "pass-through" zones in the PID tariff system between two sets of zones.
     * The zones in the from/to sets will always be excluded from the result. The order
     * of the zones in the result will be adjusted to match the order of traversal
     * given that fromZones is before toZones on the route.
     *
     * @param fromZones   zones at the first stop
     * @param toZones     zones at the last stop
     * @param includeLast true to include the last stop in the passthrough segment, which may be present in toZones.
     *                    for example, for passthrough between 2-3, it is 3, for passthrough between 2-3,4, it is also 3.
     *                    if the two zone lists share a zone, this parameter does nothing, as no extra zone is passed through.
     * @return passthrough zones
     */
    private List<String> calcPassthroughZones(List<String> fromZones, List<String> toZones, boolean includeLast) {
        fromZones = new ArrayList<>(fromZones);
        toZones = new ArrayList<>(toZones);
        if (fromZones.isEmpty() || toZones.isEmpty()) {
            return List.of();
        }
        List<String> allZones = new ArrayList<>(fromZones);
        allZones.addAll(toZones);
        allZones = new ArrayList<>(LitackaUtils.ensureZonesContiguous(allZones));
        boolean reversed = checkZonesReversed(fromZones, toZones, allZones);
        allZones.removeAll(fromZones);
        allZones.removeAll(toZones);
        allZones = new ArrayList<>(LitackaUtils.ensureZonesContiguous(allZones));
        if (reversed) {
            Collections.reverse(allZones);
        }
        if (includeLast) {
            String lastZone = reversed ? getHighestZone(toZones) : getLowestZone(toZones);
            if (!fromZones.contains(lastZone)) {
                allZones.add(lastZone);
            }
        }
        return allZones;
    }

    private String getLowestZone(List<String> zones) {
        return LitackaUtils.sortZonesForPrint(zones).get(0);
    }

    private String getHighestZone(List<String> zones) {
        List<String> sorted = LitackaUtils.sortZonesForPrint(zones);
        return sorted.get(sorted.size() - 1);
    }

    private boolean checkZonesReversed(List<String> fromZones, List<String> toZones, List<String> zoneSequence) {
        int minFrom = Integer.MAX_VALUE;
        for (String fz : fromZones) {
            minFrom = Math.min(minFrom, zoneSequence.indexOf(fz));
        }
        int maxTo = Integer.MIN_VALUE;
        for (String tz : toZones) {
            maxTo = Math.max(maxTo, zoneSequence.indexOf(tz));
        }
        return minFrom > maxTo;
    }

    private void removeNonPIDZones(List<String> zones) {
        zones.removeIf(zone -> !isPIDZone(zone));
    }

    private boolean isPIDZone(String zone) {
        if (LitackaUtils.isPragueZone(zone)) {
            return true;
        } else {
            try {
                int zoneNum = Integer.parseInt(zone);
                return zoneNum >= 1 && zoneNum <= 13;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    private List<String> getStopZonesOrEmpty(TripStopInfo stop) {
        var zones = LwtTariffZones.findEntryForTariffSystem(stop.tariffZones(), getTariffSystemId());
        if (zones != null) {
            List<String> zonesList = new ArrayList<>(zones.zones());
            if (getZoneOptions() != null) {
                // exclude zones that the ticket can not cover
                zonesList.retainAll(getZoneOptions());
            }
            if (isPID()) {
                removeNonPIDZones(zonesList);
            }
            return zonesList;
        } else {
            return List.of();
        }
    }

    private boolean hasPrepaidZones(List<String> zones, Set<String> prepaid) {
        for (String zone : zones) {
            if (prepaid.contains(zone)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNonPrepaidZone(List<String> zones, Set<String> prepaid) {
        for (String zone : zones) {
            if (!prepaid.contains(zone)) {
                return true;
            }
        }
        return false;
    }

    public LwtDevice getCurrentAutoActivationDevice() {
        return selectedAutoActivationDevice.getValue();
    }

    private <T> CompletableFuture<T> enqueueDataDownloadRequest(Function<CommType, CompletableFuture<T>> enqueueFunc, MutableLiveData<T> resultsLiveData) {
        return enqueueFunc.apply(CommType.ENQUEUE).whenCompleteAsync((result, throwable) -> {
            if (AsyncUtils.unwrapCompletionException(throwable) instanceof CancellationException) {
                // something upstream was cancelled
                return;
            }
            if (throwable == null) {
                resultsLiveData.setValue(result);
            } else {
                Log.e(TAG, "Failed to download data from device", throwable);
                autoDownloadException.setValue(throwable);
            }
        }, getApplication().getMainExecutor());
    }

    public LiveData<Boolean> getDeviceDataIsLoading() {
        return deviceDataIsLoading;
    }

    public boolean isDeviceDataLoading() {
        Boolean loading = deviceDataIsLoading.getValue();
        return loading != null && loading;
    }

    public LiveData<Boolean> getRawServerAuthenticationResult() {
        return rawServerAuthenticationResult;
    }

    public LiveData<Boolean> getServerAuthenticationResult() {
        return Transformations.map(getRawServerAuthenticationResult(), trusted -> {
            if (trusted != null) {
                return trusted || DebugFlags.isAllowUntrustedCertificates();
            } else {
                return null;
            }
        });
    }

    public boolean isDeviceTrustDecided() {
        return getRawServerAuthenticationResult().getValue() != null;
    }

    public boolean isCurrentDeviceTrusted() {
        // can not use getServerAuthenticationResult as Transformations.map only applies when there are observers
        Boolean result = getRawServerAuthenticationResult().getValue();
        return result != null && (result || DebugFlags.isAllowUntrustedCertificates());
    }

    public void setChosenZones(List<String> zones) {
        setChosenZones(zones, true);
    }

    public void setChosenZones(List<String> zones, boolean manual) {
        lastChosenZones = zones;
        chosenZones.setValue(new ZoneChoice(manual, zones));
        setActivationStopByZones(zones);
    }

    public void setChosenZonesAuto() {
        if (!activationStopTicketZones.isEmpty()) {
            lastChosenZones = activationStopTicketZones;
        }
        chosenZones.setValue(new ZoneChoice(false, activationStopTicketZones));
    }

    public boolean hasActivationStop() {
        return currentActivationStop != null;
    }

    public LiveData<ZoneChoice> getChosenZones() {
        return chosenZones;
    }

    public List<String> getLastChosenZones() {
        return lastChosenZones;
    }

    public boolean isZonesManuallyChosen() {
        ZoneChoice choice = chosenZones.getValue();
        return choice != null && choice.isManual();
    }

    public int getMaxZones() {
        return ticket.getNumZones();
    }

    public List<String> getZoneOptions() {
        return ticket.getZoneOptions();
    }

    public Duration getTicketValidityPeriod() {
        return ticket.getValidityPeriod();
    }

    public LiveData<ActivationTime> getActivationTime() {
        return activationTime;
    }

    public ActivationTime getCurrentActivationTime() {
        return currentActivationTime;
    }

    public void setActivationTime(LocalDateTime time) {
        setActivationTime(time, true);
    }

    public void setActivationTime(LocalDateTime time, boolean manual) {
        setActivationTime(new ActivationTime(manual, time));
    }

    public void setActivationTimeNow() {
        setActivationTimeNow(true);
    }

    public void setActivationTimeNow(boolean manual) {
        setActivationTime(new ActivationTime(manual, null));
    }

    public void setActivationTimeAuto() {
        setActivationTime(new ActivationTime(false, getActivationStopDepTime()));
    }

    private void setActivationTime(ActivationTime time) {
        currentActivationTime = time;
        activationTime.setValue(time);
    }

    public TripRouteInfo getSelectedRouteInfo() {
        return deviceRouteInfo.getValue();
    }

    public StopReference getCurrentActivationStop() {
        return currentActivationStop;
    }

    public boolean isActivationFromCurrentStop() {
        if (currentActivationStop != null && tviActivationStop != null) {
            return currentActivationStop.sequenceId() == tviActivationStop.sequenceId();
        }
        return false;
    }

    public void setActivationStop(StopReference stop) {
        currentActivationStop = stop;
        activationStopTicketZones = calcZonesFromActivationStop(stop, false);
        if (activationStopTicketZones.isEmpty()) {
            activationStopTicketZones = calcZonesFromActivationStop(stop, true);
        } else {
            activationStopTicketZones = trimStartEndPrepaidZones(activationStopTicketZones);
        }
        var actTime = getCurrentActivationTime();
        LocalDateTime actTimeVal;
        if (actTime == null || !actTime.isManual()) {
            LocalDateTime dep = getActivationStopDepTime();
            setActivationTime(new ActivationTime(false, dep));
            actTimeVal = dep;
        } else {
            actTimeVal = actTime.time();
        }
        var actZones = getChosenZones().getValue();
        if (actZones == null || !actZones.isManual()) {
            setChosenZonesAuto();
        }
        activationStop.setValue(stop);
        updateValidityEndStop(stop, actTimeVal);
    }

    private void setActivationStopByZones(List<String> zones) {
        StopReference stop = calcActivationStopByZones(zones, false);
        if (stop == null) {
            stop = calcActivationStopByZones(zones, true);
        }
        if (stop != null) {
            setActivationStop(stop);
        } else {
            // zones are not compatible with the route, so deselect the route
            selectAutoActivationDevice(null);
        }
    }

    private StopReference calcActivationStopByZones(List<String> zones, boolean overridePrepaid) {
        TripRouteInfo tri = getSelectedRouteInfo();
        if (tri == null) {
            return null;
        }
        Set<String> ticketZones = Set.copyOf(zones);
        Set<String> prepaidNonTicket = new HashSet<>(prepaidZones);
        if (overridePrepaid) {
            prepaidNonTicket.clear();
        } else {
            prepaidNonTicket.removeAll(ticketZones);
        }
        int firstStop = tri.trip().currentDepartureStop().sequenceId();
        for (int i = firstStop; i < tri.stopsLength(); i++) {
            TripStopInfo stop = tri.stops(i);
            List<String> stopZones = getStopZonesOrEmpty(stop);
            boolean hasTicketZone = hasPrepaidZones(stopZones, ticketZones);
            boolean hasExtPrepaid = hasPrepaidZones(stopZones, prepaidNonTicket);
            if (hasTicketZone) {
                // if there is another zone that is prepaid, we can activate the ticket
                // at a later point. but if it is the sole prepaid zone, activate it here,
                // as that is what the ticket was configured for.
                if (!hasExtPrepaid) {
                    return stop.stopRef();
                }
            }
            List<String> passthroughZones = List.of();
            if (i + 1 < tri.stopsLength()) {
                TripStopInfo nextStop = tri.stops(i + 1);
                // if there is a zone in the ticket + a prepaid zone, then include a potential passthrough zone,
                // (f.e. 3,4-4=4), as we will want to activate the ticket earlier to cover the traversed segment.
                // (even if normally we would not need the ticket for a journey that ends here, as the stop is in a prepaid zone)
                // in the case that the ticket is not able to cover this stop, continue as normal.
                List<String> zonesForPassthrough = stopZones;
                if (hasExtPrepaid) {
                    zonesForPassthrough = new ArrayList<>(zonesForPassthrough);
                    zonesForPassthrough.retainAll(prepaidNonTicket);
                }
                passthroughZones = calcPassthroughZones(zonesForPassthrough, getStopZonesOrEmpty(nextStop), hasTicketZone && hasExtPrepaid);
            }
            if (hasPrepaidZones(passthroughZones, ticketZones)) {
                return stop.stopRef();
            }
        }
        return null;
    }

    public LiveData<StopReference> getActivationStop() {
        return activationStop;
    }

    public LocalDateTime getActivationStopDepTime() {
        TripRouteInfo tripInfo = getSelectedRouteInfo();
        StopReference stop = getCurrentActivationStop();
        if (tripInfo != null) {
            if (stop != null) {
                if (stop.sequenceId() != tripInfo.trip().currentDepartureStop().sequenceId()) {
                    return LwtTime.convertLocalDateTime(tripInfo.stops(stop.sequenceId()).depTime());
                }
            }
            return defaultActivationTimeForTviStop;
        }
        return null;
    }

    private List<String> calcZonesFromActivationStop(StopReference stop, boolean overridePrepaid) {
        Set<String> prepaid = overridePrepaid ? Set.of() : Set.copyOf(prepaidZones);

        int maxZones = getMaxZones();
        List<String> zones = new ArrayList<>();
        TripRouteInfo tripInfo = getSelectedRouteInfo();
        if (tripInfo != null && stop != null) {
            for (int i = stop.sequenceId(); i < tripInfo.stopsLength(); i++) {
                List<String> stopZones = getStopZonesOrEmpty(tripInfo.stops(i));
                if (stopZones.isEmpty()) {
                    continue;
                }
                boolean alreadyPresent = false;
                String myZone = null;
                for (String zone : stopZones) {
                    if (zones.contains(zone) || prepaid.contains(zone)) {
                        alreadyPresent = true;
                        myZone = zone;
                        break;
                    }
                }
                if (!alreadyPresent) {
                    String nextZone = pickBestNextZone(stopZones, tripInfo, i);
                    zones.add(nextZone);
                    myZone = nextZone;
                }

                if (zones.size() >= maxZones) {
                    break;
                }

                if (i + 1 < tripInfo.stopsLength()) {
                    List<String> passthru = calcPassthroughZones(List.of(myZone), getStopZonesOrEmpty(tripInfo.stops(i + 1)), false);
                    for (String zone : passthru) {
                        if (!zones.contains(zone) && !(zones.isEmpty() && prepaid.contains(zone))) {
                            // zones must be contiguous, so if there were already any non-prepaid zones (i.e. zones is not empty),
                            // then we can not discard prepaid zones as it would create "gaps" in the zone sequence.
                            zones.add(zone);
                            if (zones.size() >= maxZones) {
                                break;
                            }
                        }
                    }
                }

                if (zones.size() >= maxZones) {
                    break;
                }
            }
        }

        return LitackaUtils.sortZonesForPrint(List.copyOf(zones));
    }

    private List<String> trimStartEndPrepaidZones(List<String> zones) {
        Set<String> prepaid = Set.copyOf(prepaidZones);

        zones = new ArrayList<>(LitackaUtils.sortZonesForPrint(zones));
        while (!zones.isEmpty() && prepaid.contains(zones.get(0))) {
            zones.remove(0);
        }
        while (!zones.isEmpty() && prepaid.contains(zones.get(zones.size() - 1))) {
            zones.remove(zones.size() - 1);
        }

        return zones;
    }

    private String pickBestNextZone(List<String> stopZones, TripRouteInfo route, int fromIndex) {
        for (int i = fromIndex; i < route.stopsLength(); i++) {
            TripStopInfo stop = route.stops(i);
            List<String> nextStopZones = getStopZonesOrEmpty(stop);
            for (String zone : nextStopZones) {
                if (stopZones.contains(zone)) {
                    return zone;
                }
            }
            if (i + 1 < route.stopsLength()) {
                TripStopInfo nextStop = route.stops(i + 1);
                List<String> passthroughZones = calcPassthroughZones(stop, nextStop, false);
                for (String zone : passthroughZones) {
                    if (stopZones.contains(zone)) {
                        return zone;
                    }
                }
            }
        }
        return stopZones.get(0);
    }

    public TripStopInfo getActivationStopExt() {
        return getSelectedRouteInfo().stops(getCurrentActivationStop().sequenceId());
    }

    public LiveData<Boolean> getCanActivateTicket() {
        return LiveDataUtils.combine(() -> {
            if (getActivationTime().getValue() == null) {
                return false;
            }
            var zones = getChosenZones().getValue();
            if (zones == null || zones.zones().isEmpty()) {
                return false;
            }
            return true;
        }, getActivationTime(), getChosenZones());
    }

    public boolean isCanNotUseTicketWithDevice() {
        return canNotUseTicketWithDevice;
    }

    public LocalDateTime resolveValidityStartTime() {
        LocalDateTime start = null;
        var actTime = getActivationTime().getValue();
        if (actTime != null) {
            start = actTime.time();
        }
        if (start == null) {
            start = LocalDateTime.now();
        }
        return start;
    }

    public LocalDateTime resolveValidityEndTime() {
        return resolveValidityEndTime(resolveValidityStartTime());
    }

    private LocalDateTime resolveValidityEndTime(LocalDateTime start) {
        return start.plus(ticket.getValidityPeriod());
    }

    private void updateValidityEndStop(StopReference startStop, LocalDateTime validityStartTime) {
        if (validityStartTime == null) {
            // immediate activation
            validityStartTime = LocalDateTime.now();
        }
        LocalDateTime validityEndTime = resolveValidityEndTime(validityStartTime);
        var chosenZones = getChosenZones().getValue();
        TripRouteInfo route = getSelectedRouteInfo();
        if (chosenZones == null || chosenZones.zones().isEmpty() || route == null) {
            setValidityEndStop(null);
            return;
        }

        Set<String> allowedZonesSet = new HashSet<>(chosenZones.zones());
        allowedZonesSet.addAll(prepaidZones);

        int lastIndex = -1;

        for (int i = startStop.sequenceId(); i < route.stopsLength(); ++i) {
            TripStopInfo stop = route.stops(i);
            LocalDateTime arrTime = LwtTime.convertLocalDateTime(stop.arrTime());
            if (arrTime != null && arrTime.isAfter(validityEndTime)) {
                break;
            }
            List<String> stopZones = getStopZonesOrEmpty(stop);
            List<String> passthroughZones = List.of();
            if (i + 1 < route.stopsLength()) {
                TripStopInfo nextStop = route.stops(i + 1);
                passthroughZones = calcPassthroughZones(stop, nextStop, true);
            }
            if (!hasPrepaidZones(stopZones, allowedZonesSet) && !hasPrepaidZones(passthroughZones, allowedZonesSet)) {
                break;
            }
            lastIndex = i;
        }
        if (lastIndex > startStop.sequenceId()) {
            // if valid only at one stop (such as partial ticket combined with prepaid zones), do not
            // show it
            setValidityEndStop(route.stops(lastIndex).stopRef());
        } else {
            setValidityEndStop(null);
        }
    }

    private void setValidityEndStop(StopReference stop) {
        validityEndStop.setValue(stop);
    }

    public LiveData<StopReference> getValidityEndStop() {
        return validityEndStop;
    }

    public ActivationInfo finishActivationInfo() {
        var time = getActivationTime().getValue();
        var zones = getChosenZones().getValue();
        if (time == null || zones == null || zones.zones().isEmpty()) {
            throw new IllegalStateException("Must only call this if getCanActivateTicket() is true");
        }
        var preauth = devicePreauthToken.getValue();
        return new ActivationInfo(
                time.time(),
                isActivationFromCurrentStop(),
                zones.zones(),
                (preauth != null && preauth.token().status() == PreauthorizationTokenStatus.Ok) ? ByteBufferUtils.toByteArray(preauth.token().preauthorizationToken().dataAsByteBuffer()) : null
        );
    }

    private void onTimeTick() {
        updatePreauthExpirationTime();
    }

    private void updatePreauthExpirationTime() {
        var token = devicePreauthToken.getValue();
        if (token == null || (token.token().status() != PreauthorizationTokenStatus.Ok && !MOCK_PREAUTH_ALWAYS_OK)) {
            preauthExpirationSecondsLeft.setValue(null);
        } else {
            long secondsLeft = ChronoUnit.SECONDS.between(Instant.now(), token.expiresAt());
            preauthExpirationSecondsLeft.setValue(secondsLeft);
        }
    }

    public LiveData<Long> getPreauthExpirationSecondsLeft() {
        return preauthExpirationSecondsLeft;
    }

    public void startActivateViaLwt() {
        isActivationInProgress.setValue(true);
        ActivationInfo info = finishActivationInfo();

        var resultFuture = secureLwtClient.activateTicket(
                new TicketActivationParams.Builder(ticket.getActivationToken(), "cafebabe")
                        .setActivationTime(info.time())
                        .setActivateNowIfEarlier(info.isCurrentStop())
                        .setActivationZones(info.zones())
                        .setPreauthorizationToken(info.preauthorizationToken())
                        .build(),
                CommType.ENQUEUE
        );
        currentActivationCall = resultFuture;
        resultFuture.whenCompleteAsync((result, throwable) -> {
            isActivationInProgress.setValue(false);
            currentActivationCall = null;
            if (throwable != null) {
                Log.e(TAG, "Failed to activate ticket via LWT", throwable);
                activationError.setValue(throwable);
            } else {
                activationResult.setValue(result);
            }
        }, getApplication().getMainExecutor());

        secureLwtClient.executeAsync(lwtRequestThread);
    }

    public LiveData<Boolean> getIsActivationInProgress() {
        return isActivationInProgress;
    }

    public LiveData<TicketActivationResponse> getActivationResult() {
        return activationResult;
    }

    public LiveData<Throwable> getActivationError() {
        return activationError;
    }

    public void ackActivationError() {
        activationError.setValue(null);
    }

    public static record ZoneChoice(boolean isManual, List<String> zones) {

    }

    public static record ActivationTime(boolean isManual, LocalDateTime time) {

        public boolean isNow() {
            return time() == null;
        }
    }

    public static record ActivationInfo(LocalDateTime time, boolean isCurrentStop, List<String> zones, byte[] preauthorizationToken) {

    }
}
