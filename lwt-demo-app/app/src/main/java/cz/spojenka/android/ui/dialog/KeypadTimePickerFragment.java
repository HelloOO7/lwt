package cz.spojenka.android.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TableRow;

import java.time.LocalTime;
import java.util.Arrays;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.Fragment;
import cz.spojenka.android.util.CollectionUtils;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.R;
import cz.spojenka.lwt.demoapp.databinding.DialogKeypadTimePickerBinding;

/**
 * CG-Transit style time picker dialog with keypad input. The dialog is fully compatible
 * with hardware keyboard as well.
 * It is recommended to construct the dialog using the provided {@link Builder}.
 */
public class KeypadTimePickerFragment extends Fragment implements View.OnKeyListener {

    private static final String ARG_KEYS_BOTTOM_TO_TOP = "keysBottomToTop";
    private static final String ARG_SELECTED = "selected";

    private static final String STATE_POSITION = "position";
    private static final String STATE_DIGITS = "digits";

    private static final int[] ALL_DIGITS = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    private static final int NULL_DIGIT = -1;

    private DialogKeypadTimePickerBinding binding;

    private int[] digitValues = {NULL_DIGIT, NULL_DIGIT, NULL_DIGIT, NULL_DIGIT};
    private int[] enabledDigits = ALL_DIGITS;

    private int position = 0;

    private int confirmButtonBoundKey = KeyEvent.KEYCODE_ENTER;

    private LocalTime lastValidTime;

    private OnResultListener resultListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogKeypadTimePickerBinding.inflate(getLayoutInflater());
        Bundle args = requireArguments();
        populateKeys(args.getBoolean(ARG_KEYS_BOTTOM_TO_TOP, false));

        boolean readSelectedFromArgs = true;

        if (savedInstanceState != null) {
            position = savedInstanceState.getInt(STATE_POSITION, 0);
            if (savedInstanceState.containsKey(STATE_DIGITS)) {
                readSelectedFromArgs = false;
                digitValues = savedInstanceState.getIntArray(STATE_DIGITS);
            }
        }

        if (readSelectedFromArgs) {
            LocalTime selected = BundleCompat.getSerializable(args, ARG_SELECTED, LocalTime.class);
            if (selected != null) {
                setSelected(selected);
            }
        }

        updateUI();

        binding.ibCancel.setOnClickListener(v -> {
            fireResultEvent(Activity.RESULT_CANCELED, null);
        });

        binding.tvHourField.setOnClickListener(v -> {
            position = 0;
            if (digitValues[1] == NULL_DIGIT) {
                digitValues[0] = NULL_DIGIT;
            }
            updateUI();
        });

        binding.tvMinuteField.setOnClickListener(v -> {
            if (digitValues[0] != NULL_DIGIT) {
                if (digitValues[1] == NULL_DIGIT) {
                    digitValues[1] = digitValues[0];
                    digitValues[0] = 0;
                }
                position = 2;
                if (digitValues[3] == NULL_DIGIT) {
                    digitValues[2] = NULL_DIGIT;
                }
                updateUI();
            }
        });

        binding.getRoot().setOnKeyListener(this);

        return binding.getRoot();
    }

    public void setResultListener(OnResultListener resultListener) {
        this.resultListener = resultListener;
    }

    private void fireResultEvent(int resultCode, LocalTime time) {
        if (resultListener != null) {
            resultListener.onResult(resultCode, time);
        }
    }

    public DialogInterface.OnKeyListener createOnKeyListenerForDialog() {
        return (dialog, keyCode, event) -> KeypadTimePickerFragment.this.onKey(binding.getRoot(), keyCode, event);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void ibConfirmFinishOnClick(View view) {
        LocalTime time = getSelectedTime();
        if (time != null) { // require valid input
            fireResultEvent(Activity.RESULT_OK, time);
        }
    }

    private void ibConfirmAdvanceOnClick(View view) {
        digitValues[1] = digitValues[0];
        digitValues[0] = 0;
        position = 2;
        updateUI();
    }

    private int getDigitByKeyCode(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.KEYCODE_0 -> 0;
            case KeyEvent.KEYCODE_1 -> 1;
            case KeyEvent.KEYCODE_2 -> 2;
            case KeyEvent.KEYCODE_3 -> 3;
            case KeyEvent.KEYCODE_4 -> 4;
            case KeyEvent.KEYCODE_5 -> 5;
            case KeyEvent.KEYCODE_6 -> 6;
            case KeyEvent.KEYCODE_7 -> 7;
            case KeyEvent.KEYCODE_8 -> 8;
            case KeyEvent.KEYCODE_9 -> 9;
            default -> NULL_DIGIT;
        };
    }

    private LocalTime composeResultTime() {
        int hour = digitValues[0] * 10 + digitValues[1];
        int minute = digitValues[2] * 10 + digitValues[3];
        return LocalTime.of(hour, minute);
    }

    public LocalTime getSelectedTime() {
        return lastValidTime;
    }

    public void setSelectedTime(LocalTime time) {
        setSelected(time);
        updateUI();
    }

    private void setSelected(LocalTime time) {
        if (time == null) {
            Arrays.fill(digitValues, NULL_DIGIT);
        } else {
            lastValidTime = time;
            digitValues[0] = time.getHour() / 10;
            digitValues[1] = time.getHour() % 10;
            digitValues[2] = time.getMinute() / 10;
            digitValues[3] = time.getMinute() % 10;
            position = 0;
        }
    }

    private void setupDigitKey(Button button, int digit) {
        button.setOnClickListener(new KeyClickListener(digit));
        button.setText(String.valueOf(digit));
        button.setTag(digit);
    }

    private void populateKeys(boolean bottomToTop) {
        for (int rowIndex = 0; rowIndex < 3; ++rowIndex) {
            TableRow row = (TableRow) binding.tlKeys.getChildAt(rowIndex);
            int digitBase = bottomToTop ? (7 - rowIndex * 3) : (1 + rowIndex * 3);
            for (int columnIndex = 0; columnIndex < 3; ++columnIndex) {
                Button key = (Button) row.getChildAt(columnIndex);

                int digit = digitBase + columnIndex;
                setupDigitKey(key, digit);
            }
        }
        setupDigitKey(binding.keyZero, 0);
    }

    private boolean validateDigitForPosition(int digit) {
        return Arrays.binarySearch(enabledDigits, digit) >= 0;
    }

    private void commitDigit(int digit) {
        if (position == 0) {
            if (digit > 2) {
                // do not require typing "09" or "07", just skip straight to minutes
                commitDigit(0);
            } else {
                digitValues[1] = NULL_DIGIT;
            }
        }
        if (position == 2) {
            digitValues[3] = NULL_DIGIT;
        }
        digitValues[position++] = digit;
    }

    private void updateFieldFocus() {
        View focused = switch (position) {
            case 0, 1 -> binding.tvHourField;
            case 2, 3 -> binding.tvMinuteField;
            default -> null;
        };
        for (View field : new View[]{binding.tvHourField, binding.tvMinuteField}) {
            field.setSelected(field == focused);
        }
        if (focused != null) {
            focused.requestFocus(); //for keyboard focus
        }
    }

    private void setEnabledDigits(int... digits) {
        for (int i = 0; i <= 9; ++i) {
            Button key = binding.tlKeys.findViewWithTag(i);
            key.setEnabled(false);
        }
        for (int digit : digits) {
            Button key = binding.tlKeys.findViewWithTag(digit);
            key.setEnabled(true);
        }
        enabledDigits = digits;
    }

    private void updateEnabledKeys() {
        switch (position) {
            case 0, 3 -> setEnabledDigits(ALL_DIGITS);
            case 1 -> {
                if (digitValues[0] == 2) {
                    setEnabledDigits(0, 1, 2, 3);
                } else {
                    setEnabledDigits(ALL_DIGITS);
                }
            }
            case 2 -> setEnabledDigits(0, 1, 2, 3, 4, 5);
            default -> setEnabledDigits();
        }

        updateConfirmButton();
    }

    private void updateConfirmButton() {
        @DimenRes int paddingRes = R.dimen.keyboard_time_picker_btn_image_padding;

        if (canFinishInput()) {
            setFinishButtonEnabled(true);
            binding.ibConfirm.setOnClickListener(this::ibConfirmFinishOnClick);
            binding.ibConfirm.setImageResource(R.drawable.ic_check_24px);
            confirmButtonBoundKey = KeyEvent.KEYCODE_ENTER;
        } else if (canManuallyAdvanceInput()) {
            setFinishButtonEnabled(true);
            binding.ibConfirm.setOnClickListener(this::ibConfirmAdvanceOnClick);
            binding.ibConfirm.setImageResource(R.drawable.ic_colon_24px);
            ViewUtils.setPaddingDp(binding.ibConfirm, 4);
            paddingRes = R.dimen.keyboard_time_picker_btn_colon_padding;
            //Android does not have a colon key code, instead, it is usually produced by shift+semicolon
            confirmButtonBoundKey = KeyEvent.KEYCODE_SEMICOLON;
        } else {
            setFinishButtonEnabled(false);
        }

        int paddingValue = getResources().getDimensionPixelSize(paddingRes);
        binding.ibConfirm.setPadding(paddingValue, paddingValue, paddingValue, paddingValue);
    }

    private String formatFieldContent(int... digits) {
        StringBuilder sb = new StringBuilder();
        for (int digit : digits) {
            if (digit != NULL_DIGIT) {
                sb.append(digit);
            }
        }
        return sb.toString();
    }

    private void updateFieldContent() {
        binding.tvHourField.setText(formatFieldContent(digitValues[0], digitValues[1]));
        binding.tvMinuteField.setText(formatFieldContent(digitValues[2], digitValues[3]));
    }

    private boolean canManuallyAdvanceInput() {
        return position == 1;
    }

    private boolean canFinishInput() {
        return !CollectionUtils.asList(digitValues).contains(NULL_DIGIT);
    }

    private void setFinishButtonEnabled(boolean enabled) {
        binding.ibConfirm.setVisibility(enabled ? View.VISIBLE : View.INVISIBLE);
    }

    private void updateUI() {
        updateFieldFocus();
        updateEnabledKeys();
        updateFieldContent();

        if (canFinishInput()) {
            lastValidTime = composeResultTime();
        }
    }

    private void onDigitPressed(int digit) {
        if (validateDigitForPosition(digit)) {
            commitDigit(digit);
            updateUI();
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        View clickView = null;
        if (keyCode == confirmButtonBoundKey) {
            clickView = binding.ibConfirm;
        } else if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            clickView = binding.ibCancel;
        } else {
            int digit = getDigitByKeyCode(keyCode);
            if (digit != NULL_DIGIT) {
                clickView = binding.tlKeys.findViewWithTag(digit);
            }
        }
        if (clickView != null && clickView.isEnabled() && clickView.getVisibility() == View.VISIBLE) {
            ViewUtils.performClickAnimated(clickView);
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_POSITION, position);
        outState.putIntArray(STATE_DIGITS, digitValues);
    }

    public static interface OnResultListener {

        public void onResult(int resultCode, LocalTime time);
    }

    private class KeyClickListener implements View.OnClickListener {

        private final int digit;

        public KeyClickListener(int digit) {
            this.digit = digit;
        }

        @Override
        public void onClick(View v) {
            onDigitPressed(digit);
        }
    }

    /**
     * Builder for constructing a {@link KeypadTimePickerFragment}.
     */
    public static class Builder {

        private final Bundle arguments;

        /**
         * Constructor
         */
        public Builder() {
            arguments = new Bundle();
        }

        /**
         * Set the direction of the keys in the keypad.
         *
         * @param keysBottomToTop True if the keys should be ordered from bottom to top (like a PC keyboard), false for top to bottom (like a phone keypad - default).
         * @return This builder
         */
        public Builder setKeysBottomToTop(boolean keysBottomToTop) {
            arguments.putBoolean(ARG_KEYS_BOTTOM_TO_TOP, keysBottomToTop);
            return this;
        }

        /**
         * Set the initial time selected in the dialog.
         *
         * @param selected The time to be initially selected, or null for no selection
         * @return This builder
         */
        public Builder setSelected(@Nullable LocalTime selected) {
            if (selected != null) {
                arguments.putSerializable(ARG_SELECTED, selected);
            } else {
                arguments.remove(ARG_SELECTED);
            }
            return this;
        }

        public Bundle buildArguments() {
            return arguments;
        }

        /**
         * Build the dialog.
         *
         * @return The constructed dialog
         */
        public KeypadTimePickerFragment build() {
            KeypadTimePickerFragment dialog = new KeypadTimePickerFragment();
            dialog.setArguments(arguments);
            return dialog;
        }
    }
}
