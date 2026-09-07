package cz.spojenka.lwt.demoapp;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.lwt.ICICOService;
import cz.spojenka.lwt.demoapp.databinding.ActivityCheckOutBinding;

public class CheckOutActivity extends CICOActivityBase {

    private static final String TAG = CheckOutActivity.class.getSimpleName();

    public static final ActivityResultContract<Void, Boolean> CHECK_OUT_CONTRACT = new ActivityResultContract<Void, Boolean>() {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Void input) {
            return new Intent(context, CheckOutActivity.class);
        }

        @Override
        public Boolean parseResult(int resultCode, Intent intent) {
            return resultCode == RESULT_OK;
        }
    };

    private ActivityCheckOutBinding binding;
    private ViewModel viewModel;

    @Override
    protected View doCreateView(Bundle savedInstanceState) {
        binding = ActivityCheckOutBinding.inflate(getLayoutInflater());
        viewModel = new ViewModelProvider(this).get(ViewModel.class);
        binding.confirmCheckOut.setOnSlideCompleteListener(slide -> {
            if (service != null) {
                viewModel.checkOut(service);
                binding.confirmCheckOut.setEnabled(false);
            } else {
                binding.confirmCheckOut.setCompleted(false, true);
            }
        });
        viewModel.getCheckOutDone().observe(this, done -> {
            if (done) {
                finishCheckedOut();
            }
        });
        return binding.getRoot();
    }

    @Override
    protected void onServiceConnected() {

    }

    private void finishCheckedOut() {
        setResult(RESULT_OK);
        finish();
    }

    public static class ViewModel extends AndroidViewModel {

        private final MutableLiveData<Boolean> checkOutDone = new MutableLiveData<>(false);

        public ViewModel(@NonNull Application application) {
            super(application);
        }

        public void checkOut(ICICOService service) {
            if (!service.isSessionActive()) {
                // checked out asynchronously to this activity
                checkOutDone.setValue(true);
            } else {
                service.endSession().whenCompleteAsync((o, throwable) -> {
                   checkOutDone.setValue(true);
                   if (throwable != null) {
                       Log.w(TAG, "Warning: checked out, but device operation failed, see service log");
                   }
                }, getApplication().getMainExecutor());
            }
        }

        public LiveData<Boolean> getCheckOutDone() {
            return checkOutDone;
        }
    }
}
