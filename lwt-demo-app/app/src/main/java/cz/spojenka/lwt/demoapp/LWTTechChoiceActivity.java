package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.LwdnLinkType;
import cz.spojenka.lwt.demoapp.databinding.ActivityLwtTechChoiceBinding;

public class LWTTechChoiceActivity extends BaseActivity {

    private static final String RESULT_EXTRA_TECH = LWTTechChoiceActivity.class.getName() + ".EXTRA_TECH";

    public static final ActivityResultContract<Void, Integer> PICK_TECH = new ActivityResultContract<Void, Integer>() {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void unused) {
            return new Intent(context, LWTTechChoiceActivity.class);
        }

        @Override
        public Integer parseResult(int i, @Nullable Intent intent) {
            if (i == RESULT_OK && intent.hasExtra(RESULT_EXTRA_TECH)) {
                return intent.getIntExtra(RESULT_EXTRA_TECH, LwdnLinkType.BluetoothLe);
            } else {
                return null;
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityLwtTechChoiceBinding binding = ActivityLwtTechChoiceBinding.inflate(getLayoutInflater());
        setContentView(ViewUtils.wrapInScrollView(binding.getRoot()));

        bindChoiceButton(binding.cvAware, LwdnLinkType.WifiAware);
        bindChoiceButton(binding.cvBle, LwdnLinkType.BluetoothLe);
    }

    private void bindChoiceButton(View view, int result) {
        ViewUtils.enableRipple(view);
        view.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(RESULT_EXTRA_TECH, result));
            finish();
        });
    }
}
