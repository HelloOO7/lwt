package cz.spojenka.lwt.demoapp;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.view.PermissionCheckmark;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.FeaturePrerequisite;
import cz.spojenka.lwt.demoapp.databinding.ActivityPrerequisitesBinding;
import cz.spojenka.lwt.util.PermissionRequestFlow;

public class PrerequisitesActivityBase extends BaseActivity {

    private static final Map<FeaturePrerequisite, PrerequisiteRegistration> KNOWN_PREREQUISITES = new HashMap<>();

    static {
        registerPrerequisite(FeaturePrerequisite.BLUETOOTH_CLIENT, R.string.common_prerequisites_bt_title, R.string.common_prerequisites_bt_desc_none, R.string.common_prerequisites_bt_desc_granted);
        registerPrerequisite(FeaturePrerequisite.BLUETOOTH_CLIENT_SERVER, R.string.common_prerequisites_bt_title, R.string.common_prerequisites_bt_desc_none, R.string.common_prerequisites_bt_desc_granted);
        registerPrerequisite(FeaturePrerequisite.BLUETOOTH_ON, R.string.common_prerequisites_bt_title, R.string.common_prerequisites_bt_state_desc_off, R.string.common_prerequisites_bt_state_desc_on);
        registerPrerequisite(FeaturePrerequisite.LOCATION_FOR_LE_SCAN, R.string.common_prerequisites_location_title, R.string.common_prerequisites_location_desc_none, R.string.common_prerequisites_location_desc_granted);
        registerPrerequisite(FeaturePrerequisite.BACKGROUND_LOCATION_FOR_LE_SCAN, R.string.common_prerequisites_bg_location_title, R.string.common_prerequisites_bg_location_desc_none, R.string.common_prerequisites_bg_location_desc_granted);
        registerPrerequisite(FeaturePrerequisite.NOTIFICATION_PERMISSION, R.string.common_prerequisites_notifications_title, R.string.common_prerequisites_notifications_desc_none, R.string.common_prerequisites_notifications_desc_granted);
        registerPrerequisite(FeaturePrerequisite.BATTERY_EXEMPTION, R.string.common_prerequisites_battery_title, R.string.common_prerequisites_battery_desc_none, R.string.common_prerequisites_battery_desc_granted);
        registerPrerequisite(FeaturePrerequisite.NEARBY_WIFI_DEVICES, R.string.common_prerequisites_wifi_devices_title, R.string.common_prerequisites_wifi_devices_desc_none, R.string.common_prerequisites_wifi_devices_desc_granted);
        registerPrerequisite(FeaturePrerequisite.AWARE_AVAILABLE, R.string.common_prerequisites_aware, R.string.common_prerequisites_aware_desc_none, R.string.common_prerequisites_aware_desc_granted);
        registerPrerequisite(FeaturePrerequisite.LOCATION_FOR_AWARE_SCAN, R.string.common_prerequisites_location_title, R.string.common_prerequisites_location_desc_none, R.string.common_prerequisites_location_desc_granted);
    }

    private ActivityPrerequisitesBinding binding;
    private final List<PrerequisiteBinding> prerequisiteBindings = new ArrayList<>();
    private Set<PrerequisiteBinding> shownPrerequisites = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPrerequisitesBinding.inflate(getLayoutInflater());
        setContentView(ViewUtils.wrapInScrollView(binding.getRoot()));

        binding.btnDone.setOnClickListener(v -> finish());

        createPrerequisites();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
        registerReceiver(wirelessStateChangedReceiver, filter);
    }

    private static void registerPrerequisite(FeaturePrerequisite prerequisite, @StringRes int title, @StringRes int descDenied, @StringRes int descGranted) {
        KNOWN_PREREQUISITES.put(prerequisite, new PrerequisiteRegistration(title, descDenied, descGranted));
    }

    protected void createPrerequisites() {
        onCreatePrerequisites();
        updatePrerequisites();
    }

    protected void onCreatePrerequisites() {

    }

    protected void setShownPrerequisites(FeaturePrerequisite... prerequisites) {
        if (prerequisites == null) {
            shownPrerequisites = null;
        } else {
            var prerequisiteSet = Set.of(prerequisites);
            shownPrerequisites = new HashSet<>();
            for (PrerequisiteBinding b : prerequisiteBindings) {
                if (prerequisiteSet.contains(b.prerequisite)) {
                    shownPrerequisites.add(b);
                }
            }
        }
        showPrerequisites();
        updatePrerequisites();
    }

    private void showPrerequisites() {
        binding.getRoot().removeViews(1, binding.getRoot().getChildCount() - 3);
        for (PrerequisiteBinding b : prerequisiteBindings) {
            if (shownPrerequisites == null || shownPrerequisites.contains(b)) {
                insertPrerequisiteView(b.checkmark);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(wirelessStateChangedReceiver);
    }

    private final BroadcastReceiver wirelessStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePrerequisites();
        }
    };

    protected void bindPrerequisite(FeaturePrerequisite prerequisite) {
        var registration = KNOWN_PREREQUISITES.get(prerequisite);
        if (registration != null) {
            bindPrerequisite(prerequisite, registration.title(), registration.descDenied(), registration.descGranted());
        } else {
            throw new IllegalArgumentException("Unknown prerequisite: " + prerequisite);
        }
    }

    protected void bindPrerequisite(FeaturePrerequisite prerequisite, @StringRes int title, @StringRes int descDenied, @StringRes int descGranted) {
        int viewId = createPrerequisiteView(title);
        prerequisiteBindings.add(new PrerequisiteBinding(prerequisite, viewId, descDenied, descGranted));
    }

    private int createPrerequisiteView(@StringRes int title) {
        int id = View.generateViewId();
        PermissionCheckmark checkmark = new PermissionCheckmark(this);
        checkmark.setId(id);
        checkmark.setTitle(title);
        insertPrerequisiteView(checkmark);
        return id;
    }

    private void insertPrerequisiteView(View view) {
        binding.getRoot().addView(view, binding.getRoot().getChildCount() - 2);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePrerequisites();
    }

    private void updatePrerequisites() {
        boolean allSatisfied = true;
        var toUpdate = shownPrerequisites != null ? shownPrerequisites : prerequisiteBindings;
        for (PrerequisiteBinding binding : toUpdate) {
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
            remedyFlow = prerequisite.createRemedyFlow(PrerequisitesActivityBase.this);
            this.descDenied = getString(descDenied);
            this.descGranted = getString(descGranted);
            update();
        }

        public void update() {
            if (prerequisite.isApplicable(PrerequisitesActivityBase.this) && prerequisite.checkDependenciesMet(PrerequisitesActivityBase.this)) {
                checkmark.setVisibility(PermissionCheckmark.VISIBLE);
                if (prerequisite.check(PrerequisitesActivityBase.this)) {
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

    private static record PrerequisiteRegistration(@StringRes int title, @StringRes int descDenied, @StringRes int descGranted) {

    }
}
