package cz.spojenka.lwt.demoapp;

import android.animation.LayoutTransition;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.view.View;

import com.google.android.material.color.MaterialColors;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import cz.spojenka.android.system.livedata.LiveErrorSignal;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.dialog.CommonDialogs;
import cz.spojenka.android.ui.view.LoadingPlaceholderContainer;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.FeaturePrerequisite;
import cz.spojenka.lwt.CICOService;
import cz.spojenka.lwt.ICICOService;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.demoapp.databinding.ActivityCheckInBinding;

public class CheckInActivity extends CICOActivityBase {

    public static final String EXTRA_CICO_TOKEN = CheckInActivity.class.getName() + ".EXTRA_CICO_TOKEN";

    private static final String STATE_PERMISSIONS_ASKED = CheckInActivity.class.getName() + ".STATE_PERMISSIONS_ASKED";

    private ActivityCheckInBinding binding;
    private LoadingPlaceholderContainer loading;

    private byte[] cicoToken;

    private ViewModel viewModel;
    private DeviceListViewModel deviceListViewModel;
    private InlineDevicePickerViewController devicePickerUIController;

    private boolean permissionsAsked = false;

    private ActivityResultLauncher<Intent> bluetoothOnLauncher;
    private ActivityResultLauncher<Intent> permissionLauncher;

    private final BroadcastReceiver bluetoothOnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON) {
                continueSetup();
            }
        }
    };

    @Override
    protected View doCreateView(@Nullable Bundle savedInstanceState) {
        binding = ActivityCheckInBinding.inflate(getLayoutInflater());
        binding.getRoot().getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
        cicoToken = Objects.requireNonNull(getIntent().getByteArrayExtra(EXTRA_CICO_TOKEN));
        if (savedInstanceState != null) {
            permissionsAsked = savedInstanceState.getBoolean(STATE_PERMISSIONS_ASKED, false);
        }
        if (exitIfPermissionAskedAndDenied()) {
            return new View(this);
        }

        ViewModelProvider vmp = new ViewModelProvider(this);

        viewModel = vmp.get(ViewModel.class);
        deviceListViewModel = vmp.get(DeviceListViewModel.class);

        ActivityResultCallback<ActivityResult> permResultCallback = result -> {
            if (result.getResultCode() == RESULT_OK) {
                continueSetup();
            } else {
                exitIfPermissionAskedAndDenied();
            }
        };
        bluetoothOnLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), permResultCallback);
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), permResultCallback);

        devicePickerUIController = new InlineDevicePickerViewController(binding.tripChoiceSubscreens, deviceListViewModel) {

            @Override
            protected boolean onDeviceSelected(LwtDevice device) {
                if (device == null) {
                    viewModel.cancelRequestSession(service);
                    return false;
                } else {
                    viewModel.requestSession(service, device, cicoToken);
                    return true;
                }
            }
        };
        devicePickerUIController.bind(this);

        updateConfirmSlider();
        registerReceiver(bluetoothOnReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        viewModel.getSelectedDevice().observe(this, device -> devicePickerUIController.overrideSelectedDevice(device));

        viewModel.getCheckInDevice().observe(this, device -> {
            updateConfirmSlider();
            if (device != null) {
                devicePickerUIController.markDeviceAsConfirmed(true);
                binding.confirmCheckin.setOnSlideCompleteListener(slideToActView -> viewModel.checkIn(service));
            } else {
                binding.confirmCheckin.setOnSlideCompleteListener(null);
            }
        });
        viewModel.isCheckingInLiveData().observe(this, checkingIn -> updateConfirmSlider());
        viewModel.isCheckedInLiveData().observe(this, checkedIn -> {
            if (checkedIn) {
                finish();
            }
        });

        viewModel.getCheckInError().handle(this, error -> {
            binding.confirmCheckin.setCompleted(false, true);
            CommonDialogs.newInfoDialog(this, getString(R.string.check_in_error_title), error.getMessage())
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                    .show();
        });

        return ViewUtils.wrapInScrollView(binding.getRoot());
    }

    @Override
    protected void onServiceConnected() {
        continueSetup();
    }

    private boolean exitIfPermissionAskedAndDenied() {
        if (permissionsAsked && !FeaturePrerequisite.checkCICOSatisfiedExceptBTOn(this)) {
            finish();
            return true;
        }
        return false;
    }

    private void continueSetup() {
        if (isBluetoothTurningOn()) {
            return;
        }
        if (service == null) {
            return;
        }
        if (!service.isSessionActive()) {
            prepareSessionIfCan();
        }
    }

    private boolean isBluetoothTurningOn() {
        BluetoothManager btm = getSystemService(BluetoothManager.class);
        if (btm == null) {
            return false;
        }
        BluetoothAdapter btAdapter = btm.getAdapter();
        if (btAdapter == null) {
            return false;
        }
        return btAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON;
    }

    private void prepareSessionIfCan() {
        doWithPermissions(() -> {
            if (!service.isPrepareSessionRunning()) {
                service.initSecureContext(GlobalTrustManager.createSSLContext(getApplication()));
                deviceListViewModel.bindExternalScan(service.prepareSession());
            }
        });
    }

    private void doWithPermissions(Runnable ifCan) {
        if (!FeaturePrerequisite.checkCICOSatisfied(this)) {
            permissionsAsked = true;
            if (FeaturePrerequisite.checkCICOSatisfiedExceptBTOn(this)) {
                bluetoothOnLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } else {
                permissionLauncher.launch(new Intent(this, CICOPrerequisitesActivity.class));
            }
        } else {
            ifCan.run();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothOnReceiver);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean(STATE_PERMISSIONS_ASKED, permissionsAsked);
    }

    private void updateConfirmSlider() {
        if (viewModel.isCheckingIn() || viewModel.isCheckedIn()) {
            setSliderEnabledState(false);
            setSliderEnabledStyle(true);
        } else {
            if (viewModel.hasCheckInDevice()) {
                setSliderEnabledState(true);
                setSliderEnabledStyle(true);
            } else {
                setSliderEnabledState(false);
                setSliderEnabledStyle(false);
            }
        }
    }

    private void setSliderEnabledState(boolean enabled) {
        binding.confirmCheckin.setEnabled(enabled);
    }

    private void setSliderEnabledStyle(boolean enabled) {
        var slider = binding.confirmCheckin;
        if (!enabled) {
            slider.setAlpha(0.5f);
            slider.setOuterColor(getColor(R.color.dark_gray));
            slider.setTextColor(Color.BLACK);
        } else {
            slider.setAlpha(1f);
            slider.setOuterColor(MaterialColors.getColor(slider, android.R.attr.colorPrimary));
            slider.setTextColor(Color.WHITE);
        }
        slider.setIconColor(slider.getOuterColor());
    }

    public static class ViewModel extends AndroidViewModel {

        private CompletableFuture<?> requestSessionFuture;

        private final MutableLiveData<LwtDevice> selectedDevice = new MutableLiveData<>();
        private final MutableLiveData<LwtDevice> checkInDevice = new MutableLiveData<>();

        private final MutableLiveData<Boolean> isCheckingIn = new MutableLiveData<>(false);
        private final MutableLiveData<Boolean> isCheckedIn = new MutableLiveData<>(false);

        private final LiveErrorSignal checkInError = new LiveErrorSignal();

        public ViewModel(@NonNull Application application) {
            super(application);
        }

        public void requestSession(ICICOService service, LwtDevice device, byte[] cicoToken) {
            if (requestSessionFuture != null) {
                requestSessionFuture.cancel(true);
            }
            selectedDevice.setValue(device);
            requestSessionFuture = service.requestSession(device, cicoToken);
            checkInError
                    .catchError(requestSessionFuture, nothing -> checkInDevice.setValue(device), getApplication().getMainExecutor())
                    .exceptionally(throwable -> {
                        selectedDevice.setValue(null);
                        return null;
                    });
        }

        public void cancelRequestSession(ICICOService service) {
            if (requestSessionFuture != null) {
                requestSessionFuture.cancel(true);
            }
            selectedDevice.setValue(null);
            service.cancelRequestSession();
        }

        public LiveData<LwtDevice> getSelectedDevice() {
            return selectedDevice;
        }

        public LiveData<Boolean> isCheckingInLiveData() {
            return isCheckingIn;
        }

        public boolean isCheckingIn() {
            return isCheckingIn.getValue() != null && isCheckingIn.getValue();
        }

        public MutableLiveData<LwtDevice> getCheckInDevice() {
            return checkInDevice;
        }

        public boolean hasCheckInDevice() {
            return checkInDevice.getValue() != null;
        }

        public void checkIn(ICICOService service) {
            isCheckingIn.setValue(true);
            checkInError.catchError(service.startSession(), o -> {
                isCheckedIn.setValue(true);
            }, getApplication().getMainExecutor()).whenComplete((o, throwable) -> {
                isCheckingIn.setValue(false);
            });
        }

        public boolean isCheckedIn() {
            return isCheckedIn.getValue() != null && isCheckedIn.getValue();
        }

        public LiveData<Boolean> isCheckedInLiveData() {
            return isCheckedIn;
        }

        public LiveErrorSignal getCheckInError() {
            return checkInError;
        }
    }
}
