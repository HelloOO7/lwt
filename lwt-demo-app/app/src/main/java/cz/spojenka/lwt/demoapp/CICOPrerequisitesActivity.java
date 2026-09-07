package cz.spojenka.lwt.demoapp;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.view.PermissionCheckmark;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.FeaturePrerequisite;
import cz.spojenka.lwt.demoapp.databinding.ActivityCicoPrerequisitesBinding;
import cz.spojenka.lwt.util.PermissionRequestFlow;

public class CICOPrerequisitesActivity extends BaseActivity {

    private ActivityCicoPrerequisitesBinding binding;
    private final List<PrerequisiteBinding> prerequisiteBindings = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCicoPrerequisitesBinding.inflate(getLayoutInflater());
        setContentView(ViewUtils.wrapInScrollView(binding.getRoot()));

        bindPrerequisite(FeaturePrerequisite.CICO_HARDWARE, R.id.permHardware, R.string.cico_prerequisites_hardware_desc, R.string.cico_prerequisites_hardware_desc);
        bindPrerequisite(FeaturePrerequisite.BLUETOOTH_PERMISSIONS, R.id.permBluetooth, R.string.cico_prerequisites_bt_desc_none, R.string.cico_prerequisites_bt_desc_granted);
        bindPrerequisite(FeaturePrerequisite.BLUETOOTH_ON, R.id.permBluetoothState, R.string.cico_prerequisites_bt_state_desc_off, R.string.cico_prerequisites_bt_state_desc_on);
        bindPrerequisite(FeaturePrerequisite.LOCATION_FOR_LE_SCAN, R.id.permLocation, R.string.cico_prerequisites_location_desc_none, R.string.cico_prerequisites_location_desc_granted);
        bindPrerequisite(FeaturePrerequisite.BACKGROUND_LOCATION_FOR_LE_SCAN_LP, R.id.permBackgroundLocation, R.string.cico_prerequisites_bg_location_desc_none, R.string.cico_prerequisites_bg_location_desc_granted);
        bindPrerequisite(FeaturePrerequisite.NOTIFICATION_PERMISSION, R.id.permNotifications, R.string.cico_prerequisites_notifications_desc_none, R.string.cico_prerequisites_notifications_desc_granted);
        bindPrerequisite(FeaturePrerequisite.BATTERY_EXEMPTION, R.id.permBattery, R.string.cico_prerequisites_battery_desc_none, R.string.cico_prerequisites_battery_desc_granted);

        binding.btnDone.setOnClickListener(v -> finish());

        updatePrerequisites();

        registerReceiver(BLUETOOTH_STATE_CHANGED_RECEIVER, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(BLUETOOTH_STATE_CHANGED_RECEIVER);
    }

    private final BroadcastReceiver BLUETOOTH_STATE_CHANGED_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePrerequisites();
        }
    };

    private void bindPrerequisite(FeaturePrerequisite prerequisite, @IdRes int viewId, @StringRes int descDenied, @StringRes int descGranted) {
        prerequisiteBindings.add(new PrerequisiteBinding(prerequisite, viewId, descDenied, descGranted));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePrerequisites();
    }

    private void updatePrerequisites() {
        boolean allSatisfied = true;
        for (PrerequisiteBinding binding : prerequisiteBindings) {
            binding.update();
            if (binding.prerequisite.isApplicable(this)) {
                allSatisfied &= binding.prerequisite.check(this);
            }
        }
        binding.btnDone.setEnabled(allSatisfied);
    }

    @Override
    public void finish() {
        setResult(binding.btnDone.isEnabled() ? RESULT_OK : RESULT_CANCELED);
        super.finish();
    }

    private class PrerequisiteBinding {

        private final FeaturePrerequisite prerequisite;
        private final PermissionCheckmark checkmark;
        private final PermissionRequestFlow remedyFlow;

        private final String descDenied;
        private final String descGranted;

        public PrerequisiteBinding(FeaturePrerequisite prerequisite, @IdRes int viewId, @StringRes int descDenied, @StringRes int descGranted) {
            this.prerequisite = prerequisite;
            checkmark = findViewById(viewId);
            remedyFlow = prerequisite.createRemedyFlow(CICOPrerequisitesActivity.this);
            this.descDenied = getString(descDenied);
            this.descGranted = getString(descGranted);
            update();
        }

        public void update() {
            if (prerequisite.isApplicable(CICOPrerequisitesActivity.this) && prerequisite.checkDependenciesMet(CICOPrerequisitesActivity.this)) {
                checkmark.setVisibility(PermissionCheckmark.VISIBLE);
                if (prerequisite.check(CICOPrerequisitesActivity.this)) {
                    checkmark.setDescription(descGranted);
                    checkmark.setFulfilled(true);
                    checkmark.setOnClickListener(null);
                } else {
                    checkmark.setDescription(descDenied);
                    if (remedyFlow != null) {
                        checkmark.setFulfilled(false);
                        checkmark.setOnClickListener(v -> remedyFlow.requestPermissions());
                    } else {
                        checkmark.setUnfulfillable();
                        checkmark.setOnClickListener(null);
                    }
                }
            } else {
                checkmark.setVisibility(PermissionCheckmark.GONE);
            }
        }
    }
}
