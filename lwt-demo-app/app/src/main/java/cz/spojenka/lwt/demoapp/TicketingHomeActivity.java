package cz.spojenka.lwt.demoapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.lwt.demoapp.databinding.ActivityTicketingHomeBinding;

public class TicketingHomeActivity extends BaseActivity {

    private ActivityTicketingHomeBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketingHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.setOnItemSelectedListener(menuItem -> {
            if (menuItem.getItemId() == R.id.ticketing_nav_cico) {
                startCicoOrLogin();
            } else if (menuItem.getItemId() == R.id.ticketing_nav_cico_history) {
                startActivity(new Intent(this, CICOFareHistoryActivity.class));
            } else {
                return true;
            }
            return false;
        });
    }

    private void startCicoOrLogin() {
        byte[] cicoToken = null;
        var acc = AccountRepository.getInstance(this).getAccountData();
        if (acc != null) {
            cicoToken = acc.cicoToken();
        }
        if (cicoToken == null) {
            startActivity(new Intent(this, LoginActivity.class));
        } else {
            startActivity(CheckInActivity.newIntent(this, cicoToken));
        }
    }
}
