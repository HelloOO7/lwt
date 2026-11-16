package cz.spojenka.lwt.demoapp;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;

import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.os.BundleCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import cz.dpp.praguepublictransport.LitackaUtils;
import cz.dpp.praguepublictransport.etd.LitackaETD;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.dialog.CommonDialogs;
import cz.spojenka.android.ui.dialog.ProgressDialog;
import cz.spojenka.android.ui.resources.ListFormat;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.android.util.LiveDataUtils;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwt.FeaturePrerequisite;
import cz.spojenka.lwt.InspectionSyncTransceiver;
import cz.spojenka.lwt.LocalCICOInspectionData;
import cz.spojenka.lwt.LwtAPIClient;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.LwtDeviceType;
import cz.spojenka.lwt.LwtSession;
import cz.spojenka.lwt.TicketValidationInfo;
import cz.spojenka.lwt.demoapp.databinding.ActivityTicketInspectionHomeBinding;
import cz.spojenka.lwt.util.LwtTariffZones;
import cz.spojenka.lwt.util.RazziaHeartbeatKeeper;
import cz.spojenka.lwt.util.TextMarkupConverter;
import cz.spojenka.lwtp.LwtpTLSConfig;
import cz.spojenka.lwtp.LwtpTLSPolicy;
import shaded.org.apache.commons.codec.digest.DigestUtils;

public class TicketInspectionHomeActivity extends BaseActivity {

    private static final String TAG = TicketInspectionHomeActivity.class.getSimpleName();

    private static final String STATE_LINKED_VEHICLE = "linked_vehicle";
    private static final String STATE_CURRENT_VALIDATION_ZONES = "current_validation_zones";
    private static final String STATE_CURRENT_LINE_NAME = "current_line_name";
    private static final String STATE_CURRENT_TRIP_KEY = "current_trip_key";
    private static final String STATE_VEHICLE_CICO_DATA = "vehicle_cico_data";

    private ActivityTicketInspectionHomeBinding binding;

    private TicketInspectionRepository repository;

    private ActivityResultLauncher<Intent> requestPrerequisitesLauncher;
    private ActivityResultLauncher<BarcodeFormat> scanQRLauncher;
    private ActivityResultLauncher<LwtDeviceType[]> pickDeviceLauncher;

    private LwtDevice linkedVehicle;
    private ArrayList<String> currentValidationZones;
    private String currentTripKey;
    private String currentLineName;
    private LocalCICOInspectionData currentCicoData;

    private ViewModel viewModel;

    private boolean isRazziaDialogUp = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketInspectionHomeBinding.inflate(getLayoutInflater());
        setContentView(ViewUtils.wrapInScrollView(binding.getRoot()));

        viewModel = new ViewModelProvider(this).get(ViewModel.class);
        repository = viewModel.getRepository();

        if (savedInstanceState != null) {
            linkedVehicle = BundleCompat.getParcelable(savedInstanceState, STATE_LINKED_VEHICLE, LwtDevice.class);
            currentValidationZones = savedInstanceState.getStringArrayList(STATE_CURRENT_VALIDATION_ZONES);
            currentLineName = savedInstanceState.getString(STATE_CURRENT_LINE_NAME);
            currentTripKey = savedInstanceState.getString(STATE_CURRENT_TRIP_KEY);
            currentCicoData = BundleCompat.getParcelable(savedInstanceState, STATE_VEHICLE_CICO_DATA, LocalCICOInspectionData.class);

            if (linkedVehicle != null) {
                viewModel.onVehicleConnected(linkedVehicle);
            }
        }

        requestPrerequisitesLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                repository.startLocationObserver();
            } else {
                finish();
            }
        });
        scanQRLauncher = registerForActivityResult(ZxingScanActivity.SCAN_STRING, result -> {
            if (result != null) {
                try {
                    Instant time = Instant.now();
                    LitackaETD etd = LitackaETD.parse(result);
                    inspectTicket(time, etd, false);
                } catch (IllegalArgumentException ex) {
                    Log.e(TAG, "Failed to parse QR code: " + result, ex);
                    CommonDialogs.newInfoDialog(this, R.string.error, R.string.ticket_inspection_invalid_format).show();
                }
            }
        });
        pickDeviceLauncher = registerForActivityResult(DevicePickerActivity.PICK_DEVICE, device -> {
            if (device != null) {
                linkedVehicle = device;
                viewModel.onVehicleConnected(linkedVehicle);
                updateVehicleData();
            }
        });

        binding.btnMore.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu()
                    .add(R.string.ticket_inspection_sync_keys)
                    .setIcon(R.drawable.ic_cloud_download_24px)
                    .setOnMenuItemClickListener(item -> {
                        syncKeys(true);
                        return true;
                    });
            popup.show();
        });

        binding.btnScanTicketQR.setOnClickListener(v -> {
            ensureNewestKeysAndRun(() -> scanQRLauncher.launch(BarcodeFormat.QR_CODE));
        });

        binding.btnLinkVehicle.setOnClickListener(v -> {
            pickDeviceLauncher.launch(new LwtDeviceType[]{LwtDeviceType.VEHICLE});
        });

        binding.btnRefreshVehicleData.setOnClickListener(v -> {
            if (linkedVehicle != null) {
                updateVehicleData();
            }
        });

        viewModel.getRazziaStateLiveData().observe(this, state -> {
            // if the dialog is up, keep it enabled to avoid flickering animations
            binding.switchRazzia.setEnabled(!state.isProcessing() || isRazziaDialogUp);
            binding.switchRazzia.setChecked(state.isActive());

            if (state.error() != null) {
                viewModel.ackRazziaError();

                CommonDialogs.newInfoDialog(this, R.string.error, R.string.ticket_inspection_razzia_error).show();
            }
        });

        binding.switchRazzia.setOnClickListener(v -> {
            // use onClick and not onCheckedChangeListener to avoid triggering when we programmatically change the checked state
            boolean isChecked = binding.switchRazzia.isChecked();
            if (isChecked && !repository.isStaffNumberSet()) {
                binding.switchRazzia.setChecked(false);
                CommonDialogs.newInfoDialog(this, R.string.ticket_inspection_action_required_title, R.string.ticket_inspection_must_login_message).show();
                return;
            }
            Runnable cont = () -> {
                isRazziaDialogUp = true;
                ProgressDialog.doInBackground(
                        this,
                        isChecked ? R.string.ticket_inspection_razzia_entering : R.string.ticket_inspection_razzia_exiting,
                        viewModel.setRazzia(isChecked)
                ).whenCompleteAsync((unused, throwable) -> isRazziaDialogUp = false, getLifecycleExecutor());
            };
            if (isChecked) {
                ensureNewestKeysAndRun(cont).handleAsync((unused, err) -> {
                    if (err != null) {
                        binding.switchRazzia.setChecked(false);
                    }
                    return null;
                }, getLifecycleExecutor());
            } else {
                cont.run();
            }
        });

        viewModel.getNearbyDevicesLiveData().observe(this, this::updateNearbyDevicesUI);

        updateVehicleInfoUI();
        updateLoginUI();

        if (!FeaturePrerequisite.checkInspectionSatisfied(this)) {
            requestPrerequisitesLauncher.launch(new Intent(this, InspectionPrerequisitesActivity.class));
        } else {
            repository.startLocationObserver();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        repository.stopLocationObserver();
    }

    private boolean checkNoCicoData(LitackaETD etd) {
        if (repository.isCicoTicket(etd) && currentCicoData == null) {
            CommonDialogs
                    .newInfoDialog(this, R.string.ticket_inspection_action_required_title, R.string.ticket_inspection_cico_not_connected)
                    .show();
            return false;
        }
        return true;
    }

    private boolean checkNotCheckedOut(Instant time, LitackaETD etd) {
        if (repository.isCicoTicket(etd) && repository.isCicoCheckedOut(etd, currentCicoData.sessionBlacklist())) {
            logTicketScanEvent(time, etd, TicketInspectionResult.CHECKED_OUT);
            CommonDialogs
                    .newYesNoDialog(
                            this,
                            R.string.ticket_inspection_checkedout_title,
                            R.string.ticket_inspection_checkedout_prompt,
                            (dialog, which) -> inspectTicket(time, etd, true),
                            null
                    )
                    .show();
            return false;
        }
        return true;
    }

    private boolean checkCorrectCicoDevice(LitackaETD etd) {
        if (repository.isCicoTicket(etd)) {
            Boolean keyMatch = repository.checkCicoTripKeyMatch(etd, currentTripKey);
            if (keyMatch != null) {
                if (!keyMatch) {
                    CommonDialogs
                            .newInfoDialog(this, R.string.ticket_inspection_action_required_title, R.string.ticket_inspection_cico_wrong_device)
                            .show();
                    return false;
                }
            }
            // if parsing failed/attribute was not present, ticket was probably tampered,
            // so fall through to trust check
        }
        return true;
    }

    private void finalInspectTicket(Instant time, LitackaETD etd, boolean ignoreAuthenticity) {
        boolean isCico = repository.isCicoTicket(etd);
        PublicKey forcedKey = isCico ? currentCicoData.deviceCertificate().getPublicKey() : null;
        byte[] forcedSecret = isCico ? currentCicoData.seedDerivationSecrets().get(0).token() : null;
        TicketInspectionResult authResult = repository.verifyTicketAuthenticity(etd, time, forcedKey, forcedSecret);
        if (!ignoreAuthenticity && authResult != TicketInspectionResult.OK) {
            logTicketScanEvent(time, etd, authResult);
            showUntrustedTicketDialog(time, etd);
        } else {
            logTicketScanEvent(time, etd, repository.verifyTicketTimeValidity(time, etd));
            Set<Integer> alreadySeenBy = viewModel.getPastScanDevicesForTicket(etd);
            viewModel.syncTicketAcknowledgement(etd);
            startActivity(
                    new Intent(this, TicketInspectionDetailActivity.class)
                            .putExtra(TicketInspectionDetailActivity.EXTRA_ETD, etd.encode())
                            .putExtra(TicketInspectionDetailActivity.EXTRA_INSPECTION_TIME, time.toEpochMilli())
                            .putStringArrayListExtra(TicketInspectionDetailActivity.EXTRA_INSPECTION_ZONES, currentValidationZones)
                            .putExtra(TicketInspectionDetailActivity.EXTRA_INSPECTION_TK, currentTripKey)
                            .putIntegerArrayListExtra(TicketInspectionDetailActivity.EXTRA_SEEN_BY, new ArrayList<>(alreadySeenBy))
                            .putExtra(TicketInspectionDetailActivity.EXTRA_MY_STAFF_NUMBER, repository.getStaffNumber())
            );
        }
    }

    private void logTicketScanEvent(Instant time, LitackaETD etd, TicketInspectionResult result) {
        repository.logTicketScanEvent(time, etd, result);
    }

    private void inspectTicket(Instant time, LitackaETD etd, boolean ignoreCheckedOut) {
        if (!checkNoCicoData(etd)) {
            return;
        }
        if (ignoreCheckedOut || !checkNotCheckedOut(time, etd)) {
            return;
        }
        if (!checkCorrectCicoDevice(etd)) {
            return;
        }
        finalInspectTicket(time, etd, false);
    }

    private void showUntrustedTicketDialog(Instant inspectionTime, LitackaETD etd) {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_untrusted_48px)
                .setTitle(R.string.ticket_inspection_auth_error_title)
                .setMessage(R.string.ticket_inspection_auth_error_desc)
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.ticket_inspection_auth_error_show_anyway,  (dlg, which) -> finalInspectTicket(inspectionTime, etd, true))
                .show();
    }

    private CompletableFuture<?> ensureNewestKeysAndRun(Runnable callback) {
        if (repository.hasSecretForTime(Instant.now())) {
            callback.run();
            return CompletableFuture.completedFuture(null);
        } else {
            return syncKeys(false).thenAcceptAsync(unused -> callback.run(), getLifecycleExecutor());
        }
    }

    private CompletableFuture<Void> syncKeys(boolean showOkDialog) {
        return ProgressDialog
                .doInBackground(this, R.string.ticket_inspection_syncing_keys, repository.syncWithRemoteAsync())
                .whenCompleteAsync((unused, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Failed to sync keys with remote", throwable);
                        CommonDialogs.newInfoDialog(this, R.string.error, R.string.ticket_inspection_sync_error).show();
                    } else {
                        if (showOkDialog) {
                            CommonDialogs.newInfoDialog(this, 0, R.string.ticket_inspection_sync_success).show();
                        }
                    }
                }, getLifecycleExecutor());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_LINKED_VEHICLE, linkedVehicle);
        outState.putStringArrayList(STATE_CURRENT_VALIDATION_ZONES, currentValidationZones);
        outState.putString(STATE_CURRENT_LINE_NAME, currentLineName);
        outState.putString(STATE_CURRENT_TRIP_KEY, currentTripKey);
        outState.putParcelable(STATE_VEHICLE_CICO_DATA, currentCicoData);
    }

    private void updateVehicleData() {
        LwtSession session = viewModel.lwtClient.newSession();
        CompletableFuture<TicketValidationInfo> validationInfo = viewModel.lwtClient.getTicketValidationInfo().enqueue(session);
        CompletableFuture<LocalCICOInspectionData> cicoDataCall = viewModel.lwtClient.getCICOInspectionData().enqueue(session);
        ProgressDialog.doInBackground(this, R.string.ticket_inspection_syncing_vehicle, session.executeAsync())
                .whenCompleteAsync((unused, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Failed to get ticket validation info from vehicle", throwable);
                        onVehicleLost();
                    } else {
                        onVehicleDataUpdated(validationInfo.getNow(null), cicoDataCall.getNow(null));
                    }
                }, getLifecycleExecutor());
    }

    private void onVehicleDataUpdated(TicketValidationInfo validationInfo, LocalCICOInspectionData cicoData) {
        currentValidationZones = new ArrayList<>();
        var tzEntry = LwtTariffZones.findEntryForTariffSystem(validationInfo.tariffZones(), "PID");
        if (tzEntry != null) {
            for (String zone : tzEntry.zones()) {
                if (LitackaUtils.isPragueZone(zone) || LitackaUtils.isOuterZone(zone)) {
                    currentValidationZones.add(zone);
                }
            }
        }
        var trip = validationInfo.trip().trip();
        currentLineName = TextMarkupConverter.toPlainText(trip.line().name(), false);
        currentTripKey = cicoData.tripKey();
        if (trip.globalRefId() > 0) {
            currentLineName += "/" + trip.globalRefId();
        }
        currentCicoData = cicoData;
        updateVehicleInfoUI();
    }

    private void onVehicleLost() {
        linkedVehicle = null;
        currentValidationZones = null;
        currentLineName = null;
        updateVehicleInfoUI();
        binding.tvVehicleInfo.setText(R.string.ticket_inspection_vehicle_lost);
    }

    private void updateVehicleInfoUI() {
        if (currentLineName != null) {
            binding.tvVehicleInfo.setText(getString(
                    R.string.ticket_inspection_vehicle_info_format,
                    currentLineName,
                    getResources().getQuantityString(R.plurals.zones_pid, currentValidationZones.size()),
                    ListFormat.formatList(this, currentValidationZones)
            ));
            binding.btnRefreshVehicleData.setVisibility(View.VISIBLE);
        } else {
            binding.tvVehicleInfo.setText(R.string.ticket_inspection_vehicle_none);
            binding.btnRefreshVehicleData.setVisibility(View.GONE);
        }
    }
    
    private void updateLoginUI() {
        if (repository.isStaffNumberSet()) {
            binding.btnLoginLogout.setText(R.string.ticket_inspection_logout);
            binding.tvLoginStatus.setText(getString(R.string.ticket_inspection_logged_in_as, repository.getStaffNumber()));
            binding.btnLoginLogout.setOnClickListener(v -> {
                repository.clearStaffNumber();
                updateLoginUI();
            });
        } else {
            binding.btnLoginLogout.setText(R.string.ticket_inspection_login);
            binding.tvLoginStatus.setText(R.string.ticket_inspection_not_logged_in);
            binding.btnLoginLogout.setOnClickListener(v -> {
                EditText input = new AppCompatEditText(this);
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
                input.requestFocus();
                CommonDialogs.newTextInputDialog(this, input)
                        .setTitle(R.string.ticket_inspection_login)
                        .setMessage(R.string.ticket_inspection_login_prompt)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.ticket_inspection_login_confirm, (dlg, which) -> {
                            try {
                                repository.setStaffNumber(Integer.parseInt(input.getText().toString()));
                                updateLoginUI();
                            } catch (NumberFormatException ex) {
                                CommonDialogs.newInfoDialog(this, R.string.error, R.string.ticket_inspection_invalid_staff_number).show();
                            }
                        })
                        .show();
            });
        }
    }

    private void updateNearbyDevicesUI(Set<Integer> nearbyDevices) {
        if (nearbyDevices.isEmpty()) {
            binding.tvNearbyDevices.setVisibility(View.GONE);
        } else {
            List<Integer> forPrint = new ArrayList<>(nearbyDevices);
            Collections.sort(forPrint);
            binding.tvNearbyDevices.setVisibility(View.VISIBLE);
            binding.tvNearbyDevices.setText(getString(R.string.ticket_inspection_nearby_devices_format, ListFormat.formatList(this, forPrint)));
        }
    }

    public static class ViewModel extends AndroidViewModel implements InspectionSyncTransceiver.Delegate {

        private final Executor clientCallExecutor = Executors.newSingleThreadExecutor();

        private TicketInspectionRepository repository;

        private LwdnAddress currentAddress;

        private LwtAPIClient lwtClient;
        private RazziaHeartbeatKeeper razziaKeeper;
        private InspectionSyncTransceiver syncTransceiver;
        private final Duration syncTimeSlice = Duration.ofSeconds(30);

        private final MutableLiveData<RazziaState> razziaStateLiveData = new MutableLiveData<>();
        private boolean isRazziaSyncActive = false;

        private final BroadcastReceiver screenOnOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null) {
                    // we do not have a wake lock, so we will stop the synchronization when
                    // screen is off so that we do not get cryptography exceptions because
                    // the advertisement was not updated for the next time slice.
                    switch (intent.getAction()) {
                        case Intent.ACTION_SCREEN_OFF -> syncTransceiver.stop();
                        case Intent.ACTION_SCREEN_ON -> {
                            if (isRazziaSyncActive) {
                                syncTransceiver.start();
                            }
                        }
                    }
                }
            }
        };

        public ViewModel(Application application) {
            super(application);
            repository = TicketInspectionRepository.getInstance(application);
            syncTransceiver = new InspectionSyncTransceiver(application, this);
            IntentFilter screenFilter = new IntentFilter();
            screenFilter.addAction(Intent.ACTION_SCREEN_ON);
            screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
            application.registerReceiver(screenOnOffReceiver, screenFilter);
            addCloseable(this::closeClient);
            addCloseable(syncTransceiver);
            addCloseable(() -> application.unregisterReceiver(screenOnOffReceiver));
        }

        public TicketInspectionRepository getRepository() {
            return repository;
        }

        private final Observer<RazziaHeartbeatKeeper.State> razziaStateObserver = unused -> publishRazziaState();

        private void publishRazziaState() {
            RazziaState newState = new RazziaState(razziaKeeper != null ? razziaKeeper.getStateLiveData().getValue() : null, isRazziaSyncActive);
            LiveDataUtils.setNewValue(razziaStateLiveData, newState);
        }

        public MutableLiveData<RazziaState> getRazziaStateLiveData() {
            return razziaStateLiveData;
        }

        public MutableLiveData<Set<Integer>> getNearbyDevicesLiveData() {
            return syncTransceiver.getNearbyDevicesLiveData();
        }

        public void ackRazziaError() {
            if (razziaKeeper != null) {
                razziaKeeper.ackError();
            }
        }

        public CompletableFuture<?> setRazzia(boolean isRazzia) {
            if (isRazzia) {
                if (repository.isStaffNumberSet()) {
                    isRazziaSyncActive = true;
                    syncTransceiver.start();
                }
            } else {
                isRazziaSyncActive = false;
                syncTransceiver.stop();
            }
            if (razziaKeeper != null) {
                return razziaKeeper.setRazzia(isRazzia);
            }
            publishRazziaState();
            return CompletableFuture.completedFuture(null);
        }

        private void ackTicket(int ticketIdHash) {
            syncTransceiver.setLastAdvTicketHash(ticketIdHash);
        }

        private Integer getTicketHashForSync(LitackaETD etd) {
            TicketETDParser etdp = new TicketETDParser(etd);
            Long tid = etdp.getTicketId();
            if (tid != null) {
                // we expect that IDs are serial and that between 4 billion issued tickets,
                // enough time passes that they will never physically co-exist
                return tid.intValue();
            }
            String cicoSID = etdp.getCicoSessionId();
            if (cicoSID != null) {
                // https://stackoverflow.com/questions/785091/consistency-of-hashcode-on-a-java-string
                // string hashCode is guaranteed to be consistent across all JVMs
                return cicoSID.hashCode();
            }
            return null;
        }

        public void syncTicketAcknowledgement(LitackaETD etd) {
            Integer hash = getTicketHashForSync(etd);
            if (hash != null) {
                ackTicket(hash);
            }
        }

        public Set<Integer> getPastScanDevicesForTicket(LitackaETD etd) {
            Integer hash = getTicketHashForSync(etd);
            if (hash != null) {
                return Set.copyOf(syncTransceiver.checkTicketHashAlreadyScanned(hash));
            }
            return Set.of();
        }

        private void closeClient() {
            if (lwtClient != null) {
                razziaKeeper.getStateLiveData().removeObserver(razziaStateObserver);
                AsyncUtils.runAsync(lwtClient::close);
                razziaKeeper.close();
                lwtClient = null;
                razziaKeeper = null;
            }
        }

        public void onVehicleConnected(LwtDevice vehicle) {
            LwdnAddress address = vehicle.getAddress();
            if (Objects.equals(currentAddress, address)) {
                return;
            }
            closeClient();
            setRazzia(false);
            currentAddress = address;
            lwtClient = createLwtClient(address);
            razziaKeeper = new RazziaHeartbeatKeeper(new Handler(getApplication().getMainLooper()), lwtClient, clientCallExecutor);
            razziaKeeper.getStateLiveData().observeForever(razziaStateObserver);
            publishRazziaState();
        }

        private LwtAPIClient createLwtClient(LwdnAddress address) {
            LwtAPIClient client = new LwtAPIClient(getApplication(), address);
            client.setSocketWatchdogTimeout(Duration.ofSeconds(5));
            client.useTLS(
                    new LwtpTLSConfig.Builder(address)
                            .setSSLContext(GlobalTrustManager.createSSLContext(getApplication()))
                            .setTLSPolicy(LwtpTLSPolicy.EXPLICIT_REQUIRED)
                            .build()
            );
            return client;
        }

        @Override
        public int getDeviceID() {
            return repository.getStaffNumber();
        }

        @Override
        public Duration getTimeSliceSize() {
            return syncTimeSlice;
        }

        @Override
        public byte[] provideKeyForTime(Instant time, int keySize) {
            byte[] secret = repository.getInspectionSecretForTime(time);
            if (secret == null) {
                return null;
            }
            byte[] saltedSecret = Arrays.copyOf(secret, secret.length + Long.BYTES);
            ByteBuffer.wrap(saltedSecret, secret.length, Long.BYTES).putLong(time.toEpochMilli() / syncTimeSlice.toMillis());
            return switch (keySize) {
                case 16, 32 -> Arrays.copyOf(DigestUtils.sha256(saltedSecret), keySize);
                case 64 -> DigestUtils.sha512(saltedSecret);
                default -> throw new IllegalArgumentException("Unsupported key size: " + keySize);
            };
        }
    }

    public static record RazziaState(RazziaHeartbeatKeeper.State vehicleState, boolean syncState) {

        public boolean isProcessing() {
            return vehicleState != null && vehicleState.isProcessing();
        }

        public boolean isActive() {
            return (vehicleState != null && vehicleState.isRazzia()) || syncState;
        }

        public Throwable error() {
            return vehicleState != null ? vehicleState.error() : null;
        }
    }
}
