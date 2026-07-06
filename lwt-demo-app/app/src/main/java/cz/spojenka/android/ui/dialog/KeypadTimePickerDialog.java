package cz.spojenka.android.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.time.LocalTime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentContainerView;

/**
 * Dialog wrapper around a {@link KeypadTimePickerFragment}.
 */
public class KeypadTimePickerDialog extends DialogFragment implements DialogInterface.OnKeyListener {

    private static final String ARG_REQUEST_KEY = "requestKey";
    private static final String RESULT_KEY = "result";

    private static final String STATE_FCV_ID = "fcvId";

    private KeypadTimePickerFragment implFragment;
    private DialogInterface.OnKeyListener implOnKeyListener;

    private int fcvId;

    public KeypadTimePickerDialog() {

    }

    public KeypadTimePickerDialog(String requestKey, KeypadTimePickerFragment.Builder builder) {
        Bundle args = new Bundle();
        args.putString(ARG_REQUEST_KEY, requestKey);
        args.putAll(builder.buildArguments());
        setArguments(args);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setOnKeyListener(this);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        getChildFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
            if (fragment instanceof KeypadTimePickerFragment keypadTimePickerFragment) {
                this.implFragment = keypadTimePickerFragment;
                this.implOnKeyListener = keypadTimePickerFragment.createOnKeyListenerForDialog();

                implFragment.setResultListener((resultCode, time) -> {
                    if (resultCode == Activity.RESULT_OK) {
                        setResult(time);
                    } else {
                        setResultCancelled();
                    }
                    dismiss();
                });
            }
        });
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentContainerView fcv = new FragmentContainerView(requireContext());
        if (savedInstanceState != null) {
            fcvId = savedInstanceState.getInt(STATE_FCV_ID, View.generateViewId());
        } else {
            fcvId = View.generateViewId();
        }
        fcv.setId(fcvId);
        return fcv;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(fcvId, KeypadTimePickerFragment.class, getArguments())
                    .commit();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FCV_ID, fcvId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        implFragment = null;
        implOnKeyListener = null;
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (implOnKeyListener != null) {
            return implOnKeyListener.onKey(dialog, keyCode, event);
        } else {
            return false;
        }
    }

    private String getRequestKey() {
        return requireArguments().getString(ARG_REQUEST_KEY);
    }

    private void setResult(LocalTime result) {
        Bundle resultBundle = new Bundle();
        resultBundle.putSerializable(RESULT_KEY, result);
        getParentFragmentManager().setFragmentResult(getRequestKey(), resultBundle);
    }

    private void setResultCancelled() {
        getParentFragmentManager().setFragmentResult(getRequestKey(), new Bundle());
    }

    public static LocalTime getResult(Bundle result) {
        return BundleCompat.getSerializable(result, RESULT_KEY, LocalTime.class);
    }
}
