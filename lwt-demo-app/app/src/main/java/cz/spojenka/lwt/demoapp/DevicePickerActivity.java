package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;
import androidx.lifecycle.ViewModelProvider;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.LwtDeviceType;

public class DevicePickerActivity extends DeviceListActivity {

    public static final String RESULT_EXTRA_SELECTED_DEVICE = DeviceListActivity.class.getName() + ".EXTRA_SELECTED_DEVICE";

    public static final ActivityResultContract<LwtDeviceType[], LwtDevice> PICK_DEVICE = new ActivityResultContract<>() {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, LwtDeviceType[] lwtDeviceTypes) {
            return new Intent(context, DevicePickerActivity.class).putExtra(EXTRA_DEVICE_TYPE_FILTER, lwtDeviceTypes);
        }

        @Override
        public LwtDevice parseResult(int resultCode, @Nullable Intent intent) {
            if (resultCode == RESULT_OK && intent != null) {
                return IntentCompat.getParcelableExtra(intent, RESULT_EXTRA_SELECTED_DEVICE, LwtDevice.class);
            }
            return null;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        DeviceListViewModel vm = new ViewModelProvider(this).get(DeviceListViewModel.class);
        vm.setUseContinuousScan(true);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDeviceSelected(LwtDevice device) {
        setResult(RESULT_OK, new Intent().putExtra(RESULT_EXTRA_SELECTED_DEVICE, device));
        finish();
    }
}
