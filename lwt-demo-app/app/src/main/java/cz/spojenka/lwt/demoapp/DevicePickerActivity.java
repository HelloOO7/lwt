package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.LwtDeviceType;

public class DevicePickerActivity extends DeviceListActivity {

    public static final String RESULT_EXTRA_SELECTED_DEVICE = DeviceListActivity.class.getName() + ".EXTRA_SELECTED_DEVICE";

    public static final ActivityResultContract<LwtDeviceType[], LwtDevice> PICK_DEVICE = new ActivityResultContract<>() {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, LwtDeviceType[] lwtDeviceTypes) {
            return new Intent(context, DeviceListActivity.class).putExtra(EXTRA_DEVICE_TYPE_FILTER, lwtDeviceTypes);
        }

        @Override
        public LwtDevice parseResult(int resultCode, @Nullable Intent intent) {
            if (resultCode == RESULT_OK && intent != null) {
                return intent.getParcelableExtra(RESULT_EXTRA_SELECTED_DEVICE);
            }
            return null;
        }
    };

    @Override
    protected void onDeviceSelected(LwtDevice device) {
        setResult(RESULT_OK, new Intent().putExtra(RESULT_EXTRA_SELECTED_DEVICE, device));
        finish();
    }
}
