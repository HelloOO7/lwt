package cz.spojenka.lwt.demoapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import cz.dpp.praguepublictransport.LitackaUtils;
import cz.dpp.praguepublictransport.etd.LitackaETD;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.dialog.CommonDialogs;
import cz.spojenka.android.ui.dialog.ProgressDialog;
import cz.spojenka.android.ui.resources.ListFormat;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.lwt.CommType;
import cz.spojenka.lwt.LwtAPIClient;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.LwtDeviceType;
import cz.spojenka.lwt.TicketValidationInfo;
import cz.spojenka.lwt.demoapp.databinding.ActivityTicketInspectionHomeBinding;
import cz.spojenka.lwt.util.LwtTariffZones;
import cz.spojenka.lwt.util.TextMarkupConverter;
import cz.spojenka.lwtp.LwtpTLSConfig;
import cz.spojenka.lwtp.LwtpTLSPolicy;

public class TicketInspectionHomeActivity extends BaseActivity {

    private static final String TAG = TicketInspectionHomeActivity.class.getSimpleName();

    private static final String STATE_LINKED_VEHICLE = "linked_vehicle";
    private static final String STATE_CURRENT_VALIDATION_ZONES = "current_validation_zones";
    private static final String STATE_CURRENT_LINE_NAME = "current_line_name";
    private static final String STATE_CURRENT_TRIP_KEY = "current_trip_key";

    private ActivityTicketInspectionHomeBinding binding;

    private TicketInspectionRepository repository;

    private ActivityResultLauncher<BarcodeFormat> scanQRLauncher;
    private ActivityResultLauncher<LwtDeviceType[]> pickDeviceLauncher;

    private LwtDevice linkedVehicle;
    private ArrayList<String> currentValidationZones;
    private String currentTripKey;
    private String currentLineName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketInspectionHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new TicketInspectionRepository(getApplication());

        if (savedInstanceState != null) {
            linkedVehicle = BundleCompat.getParcelable(savedInstanceState, STATE_LINKED_VEHICLE, LwtDevice.class);
            currentValidationZones = savedInstanceState.getStringArrayList(STATE_CURRENT_VALIDATION_ZONES);
            currentLineName = savedInstanceState.getString(STATE_CURRENT_LINE_NAME);
            currentTripKey = savedInstanceState.getString(STATE_CURRENT_TRIP_KEY);
        }

        scanQRLauncher = registerForActivityResult(ZxingScanActivity.SCAN_STRING, result -> {
            if (result != null) {
                try {
                    Instant time = Instant.now();
                    LitackaETD etd = LitackaETD.parse(result);
                    if (!repository.verifyTicketAuthenticity(etd, time)) {
                        new MaterialAlertDialogBuilder(this)
                                .setIcon(R.drawable.ic_untrusted_48px)
                                .setTitle(R.string.ticket_inspection_auth_error_title)
                                .setMessage(R.string.ticket_inspection_auth_error_desc)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    } else {
                        startActivity(
                                new Intent(this, TicketInspectionDetailActivity.class)
                                        .putExtra(TicketInspectionDetailActivity.EXTRA_ETD, result)
                                        .putExtra(TicketInspectionDetailActivity.EXTRA_INSPECTION_TIME, time.toEpochMilli())
                                        .putStringArrayListExtra(TicketInspectionDetailActivity.EXTRA_INSPECTION_ZONES, currentValidationZones)
                                        .putExtra(TicketInspectionDetailActivity.EXTRA_INSPECTION_TK, currentTripKey)
                        );
                    }
                } catch (IllegalArgumentException ex) {
                    Log.e(TAG, "Failed to parse QR code: " + result, ex);
                    CommonDialogs.newInfoDialog(this, R.string.error, R.string.ticket_inspection_invalid_format);
                }
            }
        });
        pickDeviceLauncher = registerForActivityResult(DevicePickerActivity.PICK_DEVICE, device -> {
            if (device != null) {
                linkedVehicle = device;
                updateVehicleData();
            }
        });

        binding.btnSyncKeys.setOnClickListener(v -> syncKeys(true));

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

        updateVehicleInfoUI();
    }

    private void ensureNewestKeysAndRun(Runnable callback) {
        if (repository.hasSecretForTime(Instant.now())) {
            callback.run();
        } else {
            syncKeys(false).thenAcceptAsync(unused -> callback.run(), getLifecycleExecutor());
        }
    }

    private CompletableFuture<Void> syncKeys(boolean showOkDialog) {
        return ProgressDialog
                .doInBackground(this, R.string.ticket_inspection_syncing_keys, repository.syncWithRemoteAsync())
                .whenCompleteAsync((unused, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Failed to sync keys with remote", throwable);
                        CommonDialogs.newInfoDialog(this, R.string.error, R.string.ticket_inspection_sync_error);
                    } else {
                        if (showOkDialog) {
                            CommonDialogs.newInfoDialog(this, 0, R.string.ticket_inspection_sync_success);
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
    }

    private void updateVehicleData() {
        LwtAPIClient client = new LwtAPIClient(this, linkedVehicle.getAddress());
        client.setSocketWatchdogTimeout(Duration.ofSeconds(5));
        client.useTLS(
                new LwtpTLSConfig.Builder(linkedVehicle.getAddress())
                        .setSSLContext(GlobalTrustManager.createSSLContext(getApplication()))
                        .setTLSPolicy(LwtpTLSPolicy.EXPLICIT_REQUIRED)
                        .build()
        );

        ProgressDialog.doInBackground(this, R.string.ticket_inspection_syncing_vehicle, client.getTicketValidationInfo(CommType.ENQUEUE))
                .whenCompleteAsync((ticketValidationInfo, throwable) -> {
                    if (ticketValidationInfo != null) {
                        onVehicleDataUpdated(ticketValidationInfo);
                    } else {
                        Log.e(TAG, "Failed to get ticket validation info from vehicle", throwable);
                        onVehicleLost();
                    }
                }, getLifecycleExecutor());

        client.executeAsync();

        AsyncUtils.run(client::close);
    }

    private void onVehicleDataUpdated(TicketValidationInfo validationInfo) {
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
        currentTripKey = Long.toString(trip.line().globalRefId());
        if (trip.globalRefId() > 0) {
            currentLineName += "/" + trip.globalRefId();
            currentTripKey += "/" + trip.globalRefId();
        }
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
}
