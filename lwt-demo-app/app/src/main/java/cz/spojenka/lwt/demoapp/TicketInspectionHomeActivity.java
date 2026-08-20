package cz.spojenka.lwt.demoapp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.zxing.BarcodeFormat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

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

    private ActivityTicketInspectionHomeBinding binding;

    private TicketInspectionRepository repository;

    private ActivityResultLauncher<BarcodeFormat> scanQRLauncher;
    private ActivityResultLauncher<LwtDeviceType[]> pickDeviceLauncher;

    private LwtDevice linkedVehicle;
    private ArrayList<String> currentValidationZones;
    private String currentLineName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketInspectionHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            linkedVehicle = BundleCompat.getParcelable(savedInstanceState, STATE_LINKED_VEHICLE, LwtDevice.class);
            currentValidationZones = savedInstanceState.getStringArrayList(STATE_CURRENT_VALIDATION_ZONES);
            currentLineName = savedInstanceState.getString(STATE_CURRENT_LINE_NAME);
        }

        scanQRLauncher = registerForActivityResult(ZxingScanActivity.SCAN_STRING, result -> {
            if (result != null) {
                try {
                    LitackaETD etd = LitackaETD.parse(result);
                    if (!repository.verifyTicketAuthenticity(etd, Instant.now())) {
                        new MaterialAlertDialogBuilder(this)
                                .setIcon(R.drawable.ic_untrusted_48px)
                                .setTitle(R.string.ticket_inspection_auth_error_title)
                                .setMessage(R.string.ticket_inspection_auth_error_desc)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    } else {

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

        repository = new TicketInspectionRepository(getApplication());

        binding.btnSyncKeys.setOnClickListener(v -> {
            ProgressDialog.doInBackground(this, R.string.ticket_inspection_syncing_keys, repository.syncWithRemoteAsync());
        });

        binding.btnScanTicketQR.setOnClickListener(v -> {
            scanQRLauncher.launch(BarcodeFormat.QR_CODE);
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_LINKED_VEHICLE, linkedVehicle);
        outState.putStringArrayList(STATE_CURRENT_VALIDATION_ZONES, currentValidationZones);
        outState.putString(STATE_CURRENT_LINE_NAME, currentLineName);
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
        currentLineName = TextMarkupConverter.toPlainText(validationInfo.trip().trip().line().name(), false);
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
