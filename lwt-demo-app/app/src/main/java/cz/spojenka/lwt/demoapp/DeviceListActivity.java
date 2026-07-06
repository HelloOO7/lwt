package cz.spojenka.lwt.demoapp;

import android.os.Bundle;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import cz.spojenka.android.polyfills.BundleCompat;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.LwtDeviceType;
import cz.spojenka.lwt.demoapp.databinding.ActivityDeviceListBinding;

public class DeviceListActivity extends BaseActivity {

    public static final String EXTRA_DEVICE_TYPE_FILTER = DeviceListActivity.class.getName() + ".EXTRA_DEVICE_TYPE_FILTER";

    private ActivityDeviceListBinding binding;
    private DeviceListViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(DeviceListViewModel.class);
        if (getIntent().getExtras() != null) {
            viewModel.setDeviceTypes(BundleCompat.getParcelableArray(getIntent().getExtras(), EXTRA_DEVICE_TYPE_FILTER, LwtDeviceType.class));
        }
        viewModel.startScan();

        new DeviceListViewController(binding.rvDeviceList, viewModel) {

            @Override
            protected void onDeviceSelected(LwtDevice device) {
                DeviceListActivity.this.onDeviceSelected(device);
            }

            @Override
            protected void notifyVisibleLoadingState(boolean isLoading) {
                if (!isLoading) {
                    binding.getRoot().setRefreshing(false);
                }
            }
        }.bind(this);

        viewModel.getScanError().observe(this, error -> {
            if (error != null) {
                binding.getRoot().setRefreshing(false);

                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.error_comm)
                        .setMessage(error.getMessage())
                        .setPositiveButton(android.R.string.ok, (dlg, which) -> finish())
                        .setCancelable(false)
                        .show();

                viewModel.ackScanError();
            }
        });

        binding.getRoot().setOnRefreshListener(viewModel::reloadIfNotLoading);
        int srlEnd = binding.getRoot().getProgressViewEndOffset();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int offset = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top - binding.getRoot().getProgressCircleDiameter();
            binding.getRoot().setProgressViewOffset(
                    getSupportActionBar() == null,
                    offset,
                    offset + srlEnd
            );
            return insets;
        });
    }

    protected void onDeviceSelected(LwtDevice device) {

    }
}
