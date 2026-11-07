package cz.spojenka.lwt.demoapp;

import android.os.Bundle;

import com.google.android.material.tabs.TabLayoutMediator;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.dialog.CommonDialogs;
import cz.spojenka.android.ui.helpers.SimpleFragmentStateAdapter;
import cz.spojenka.android.ui.helpers.TabConfigurationHelper;
import cz.spojenka.lwt.demoapp.databinding.ActivityCicoFareHistoryBinding;

public class CICOFareHistoryActivity extends BaseActivity {

    private CICOFareHistoryViewModel viewModel;
    private ErrorAndRetryScreen errorScreen;

    private ActivityResultLauncher<Void> loginLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CICOFareHistoryViewModel.class);

        ActivityCicoFareHistoryBinding binding = ActivityCicoFareHistoryBinding.inflate(getLayoutInflater());
        TabConfigurationHelper tabConfig = new TabConfigurationHelper(
                new TabConfigurationHelper.Tab(R.string.cico_history_active_header),
                new TabConfigurationHelper.Tab(R.string.cico_history_cleared_header)
        );

        binding.viewPager.setAdapter(new SimpleFragmentStateAdapter(this).withFragments(
                () -> CICOTravelListFragment.newInstance(CICOTravelListFragment.TYPE_ACTIVE),
                () -> CICOTravelListFragment.newInstance(CICOTravelListFragment.TYPE_HISTORY)
        ));

        new TabLayoutMediator(binding.tabs, binding.viewPager, tabConfig).attach();

        errorScreen = new ErrorAndRetryScreen(this, binding.getRoot());
        errorScreen.bindErrorSignal(this, viewModel.getLoadError(), this::loadOrCallLogin);

        viewModel.getEvaluatedSessionResult().observe(this, fare -> {
            if (fare != null) {
                viewModel.ackEvaluatedSessionResult();
                startActivity(CICOFareSummaryActivity.newIntent(this, fare));
            }
        });
        viewModel.getEvaluateSessionError().observe(this, err -> {
            CommonDialogs.newInfoDialog(this, getString(R.string.error), err.getMessage()).show();
        });

        setContentView(errorScreen.getRoot());

        loginLauncher = registerForActivityResult(LoginActivity.LOG_IN, unused -> {
            if (!tryStartLoadIfLoggedIn()) {
                finish();
            }
        });

        loadOrCallLogin();
    }

    private void loadOrCallLogin() {
        if (!tryStartLoadIfLoggedIn()) {
            loginLauncher.launch(null);
        }
    }

    private boolean tryStartLoadIfLoggedIn() {
        if (AccountRepository.getInstance(getApplicationContext()).isLoggedIn()) {
            viewModel.load();
            return true;
        }
        return false;
    }
}
