package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.spojenka.lwdn.BluetoothLwdnScanner;
import cz.spojenka.lwdn.WifiAwareLwdnScanner;
import cz.spojenka.lwt.FeaturePrerequisite;
import cz.spojenka.lwt.LwdnLinkType;

public class LWTPrerequisitesActivity extends PrerequisitesActivityBase {

    private static final String EXTRA_HW_TYPE = LWTPrerequisitesActivity.class.getName() + ".EXTRA_HW_TYPE";

    private static final String STATE_CHOSEN_HW_TYPE = "chosenHwType";

    private static final int HW_UNDECIDED = -1;
    public static final int HW_DEFAULT = -2;
    public static final int HW_BLE = LwdnLinkType.BluetoothLe;
    public static final int HW_AWARE = LwdnLinkType.WifiAware;

    private ActivityResultLauncher<Void> techChoiceLauncher;
    private int chosenHwType = HW_UNDECIDED;

    public static Intent newIntent(Context context, int hwType) {
        return new Intent(context, LWTPrerequisitesActivity.class)
                .putExtra(EXTRA_HW_TYPE, hwType);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        techChoiceLauncher = registerForActivityResult(LWTTechChoiceActivity.PICK_TECH, result -> {
            if (result != null) {
                chosenHwType = result;
                applyHwType();
            } else {
                finish();
            }
        });
        if (savedInstanceState != null) {
            chosenHwType = savedInstanceState.getInt(STATE_CHOSEN_HW_TYPE, HW_UNDECIDED);
        }
        super.onCreate(savedInstanceState);
        applyHwType();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CHOSEN_HW_TYPE, chosenHwType);
    }

    @Override
    protected void onCreatePrerequisites() {
        bindAllPrerequisites(FeaturePrerequisite.LWT_OVER_BLE);
        bindAllPrerequisites(FeaturePrerequisite.LWT_OVER_AWARE);
    }

    private void applyHwType() {
        int hwType = chosenHwType;
        if (hwType == HW_UNDECIDED) {
            hwType = getIntent().getIntExtra(EXTRA_HW_TYPE, HW_DEFAULT);
        }
        hwType = resolveHwTypeOrCallChoice(hwType);

        if (hwType != HW_UNDECIDED) {
            // we must add all prerequisites at the start, as registerForActivityResult (in the checkmarks) must be done
            // only once in onCreate

            FeaturePrerequisite[] prerequisites = hwType == HW_AWARE ? FeaturePrerequisite.LWT_OVER_AWARE : FeaturePrerequisite.LWT_OVER_BLE;
            setShownPrerequisites(prerequisites);
        }
    }

    private void bindAllPrerequisites(FeaturePrerequisite[] prerequisites) {
        for (FeaturePrerequisite fp : prerequisites) {
            if (fp == FeaturePrerequisite.BLE_HARDWARE) {
                bindPrerequisite(fp, R.string.common_prerequisites_hardware_title, R.string.lwt_prerequisites_hardware_desc_bt, R.string.lwt_prerequisites_hardware_desc_bt);
            } else if (fp == FeaturePrerequisite.AWARE_HARDWARE) {
                bindPrerequisite(fp, R.string.common_prerequisites_hardware_title, R.string.lwt_prerequisites_hardware_desc_aware, R.string.lwt_prerequisites_hardware_desc_aware);
            } else {
                bindPrerequisite(fp);
            }
        }
    }

    private int resolveHwTypeOrCallChoice(int hwType) {
        if (hwType == HW_DEFAULT) {
            boolean bleSupported = BluetoothLwdnScanner.isSupported(this);
            boolean awareSupported = WifiAwareLwdnScanner.isSupported(this);
            if (bleSupported && awareSupported) {
                techChoiceLauncher.launch(null);
                return HW_UNDECIDED;
            } else if (bleSupported) {
                return HW_BLE;
            } else if (awareSupported) {
                return HW_AWARE;
            } else {
                // show BLE (with unavailable marker) if nothing is available
                return HW_BLE;
            }
        } else {
            return hwType;
        }
    }
}
