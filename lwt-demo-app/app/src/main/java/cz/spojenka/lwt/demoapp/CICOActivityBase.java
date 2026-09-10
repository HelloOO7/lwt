package cz.spojenka.lwt.demoapp;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.FrameLayout;

import com.ncorti.slidetoact.SlideToActView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.view.LoadingPlaceholderContainer;
import cz.spojenka.android.ui.view.LoadingScreen;
import cz.spojenka.lwt.CICOService;
import cz.spojenka.lwt.ICICOService;
import cz.spojenka.lwt.demoapp.databinding.CicoLoadingScreenBinding;

public abstract class CICOActivityBase extends BaseActivity {

    protected ICICOService service;
    private boolean serviceConnectionRequested = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            CICOActivityBase.this.service = (ICICOService) service;
            startService(CICOService.startIntent(CICOActivityBase.this, CICOForegroundController.class));
            CICOActivityBase.this.onServiceConnected();
            loading.hide();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            loading.show();
        }
    };

    private LoadingScreen loading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View loadingView = CicoLoadingScreenBinding.inflate(getLayoutInflater()).getRoot();
        View contentView = doCreateView(savedInstanceState);
        loading = new LoadingScreen(loadingView, contentView);
        FrameLayout root = new FrameLayout(this);
        root.addView(loadingView);
        root.addView(contentView);
        loading.show();
        setContentView(root);
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
