package cz.spojenka.lwt.demoapp;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;

import androidx.annotation.Nullable;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.view.LoadingPlaceholderContainer;
import cz.spojenka.lwt.CICOService;
import cz.spojenka.lwt.ICICOService;

public abstract class CICOActivityBase extends BaseActivity {

    protected ICICOService service;
    private boolean serviceConnectionRequested = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CICOActivityBase.this.service = (ICICOService) service;
            startService(CICOService.startIntent(CICOActivityBase.this, CICOForegroundController.class));
            CICOActivityBase.this.onServiceConnected();
            loading.showContent();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            loading.showLoading();
        }
    };

    private LoadingPlaceholderContainer loading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loading = LoadingPlaceholderContainer.wrapScreens(this, doCreateView(savedInstanceState));
        loading.setLoadingDescription(R.string.check_in_service_connecting);
        setContentView(loading.getRoot());
        if (CICOService.isSupported(this)) {
            // we will connect to the service regardless of permissions
            // - it can always be started fine, whether it can be used is a different story.
            // the only scenario where we will not attempt to connect is if there is no
            // bluetooth hardware/API
            checkConnectToService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectFromService();
    }

    private void checkConnectToService() {
        if (serviceConnectionRequested) {
            return;
        }
        serviceConnectionRequested = true;
        bindService(CICOService.bindIntent(this), serviceConnection, BIND_AUTO_CREATE);
    }

    private void disconnectFromService() {
        if (serviceConnectionRequested) {
            if (service != null) {
                if (service.isPrepareSessionRunning()) {
                    service.cancelPrepareSession();
                }
                if (!service.isSessionActive()) {
                    stopService(CICOService.stopIntent(this));
                }
            }
            unbindService(serviceConnection);
            serviceConnectionRequested = false;
        }
    }

    protected abstract View doCreateView(Bundle savedInstanceState);

    protected abstract void onServiceConnected();
}
