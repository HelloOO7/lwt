package cz.spojenka.lwt.demoapp;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import java.util.Objects;

import androidx.annotation.Nullable;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.lwt.FeaturePrerequisite;
import cz.spojenka.lwt.CICOService;
import cz.spojenka.lwt.ICICOService;

public class CheckInActivity extends BaseActivity {

    public static final String EXTRA_CICO_TOKEN = CheckInActivity.class.getName() + ".EXTRA_CICO_TOKEN";

    private ICICOService service;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CheckInActivity.this.service = (ICICOService) service;
            //startService(CICOService.startIntent(CheckInActivity.this, cicoToken));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    private byte[] cicoToken;
    private boolean serviceConnectionRequested = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cicoToken = Objects.requireNonNull(getIntent().getByteArrayExtra(EXTRA_CICO_TOKEN));
        if (CICOService.isSupported(this)) {
            if (!FeaturePrerequisite.checkCICOSatisfied(this)) {
                startActivity(new Intent(this, CICOPrerequisitesActivity.class));
            }
        } else {
            disconnectFromService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (FeaturePrerequisite.checkCICOSatisfied(this)) {
            checkConnectToService();
        } else {
            disconnectFromService();
        }
    }

    private void checkConnectToService() {
        if (serviceConnectionRequested) {
            return;
        }
        bindService(CICOService.bindIntent(this), serviceConnection, BIND_AUTO_CREATE);
    }

    private void disconnectFromService() {
        if (serviceConnectionRequested) {
            if (service != null) {
                if (!service.isSessionActive()) {
                    stopService(CICOService.stopIntent(this));
                }
            }
            unbindService(serviceConnection);
            serviceConnectionRequested = false;
        }
    }
}
