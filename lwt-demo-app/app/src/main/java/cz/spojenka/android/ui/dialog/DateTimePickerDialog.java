package cz.spojenka.android.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Supplier;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;
import cz.spojenka.android.util.DateTimeUtils;
import cz.spojenka.lwt.demoapp.R;
import cz.spojenka.lwt.demoapp.databinding.DialogDatetimePickerBinding;

/**
 * Picker for a date and time, which uses a {@link cz.spojenka.android.ui.view.DateSwiper} for the date
 * and a {@link com.google.android.material.timepicker.MaterialTimePicker} for the time.
 *
 * @see Builder
 */
public class DateTimePickerDialog extends DialogFragment {

    private static final String ARG_REQUEST_KEY = "requestKey";
    private static final String ARG_MIN_DATETIME = "minDateTime";
    private static final String ARG_MIN_DATETIME_NOW_OFFSET = "minDateTimeNowOffset";
    private static final String ARG_SELECTED = "selected";

    public static final String RESULT_KEY = "result";

    private Supplier<LocalDateTime> getMinimum;

    private DialogDatetimePickerBinding binding;

    private LocalTime lastExceededTime = null;
    private Toast lastToast;

    private KeypadTimePickerFragment timePicker;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        getChildFragmentManager().addFragmentOnAttachListener((fragmentManager, fragment) -> {
            if (fragment instanceof KeypadTimePickerFragment keypadTimePickerFragment) {
                this.timePicker = keypadTimePickerFragment;

                timePicker.setResultListener((resultCode, time) -> {
                    if (resultCode == Activity.RESULT_CANCELED || time == null) {
                        Dialog dialog = getDialog();
                        if (dialog != null) {
                            dialog.cancel();
                        }
                        setResultCanceled();
                    } else {
                        onTimeSelected(time);
                    }
                });
            }
        });
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogDatetimePickerBinding.inflate(getLayoutInflater());
        Bundle arguments = requireArguments();
        if (arguments.containsKey(ARG_MIN_DATETIME)) {
            LocalDateTime minimum = BundleCompat.getSerializable(arguments, ARG_MIN_DATETIME, LocalDateTime.class);
            if (minimum != null) {
                getMinimum = () -> minimum;
            }
        } else if (arguments.containsKey(ARG_MIN_DATETIME_NOW_OFFSET)) {
            Duration offset = BundleCompat.getSerializable(arguments, ARG_MIN_DATETIME_NOW_OFFSET, Duration.class);
            getMinimum = () -> LocalDateTime.now().plus(offset);
        }
        if (getMinimum != null) {
            binding.dateSwiper.setMinimumDate(getMinimum.get().toLocalDate());
        }

        LocalDateTime selected = BundleCompat.getSerializable(arguments, ARG_SELECTED, LocalDateTime.class);
        if (selected != null) {
            binding.dateSwiper.setValue(selected.toLocalDate());
        }

        if (getMinimum != null) {
            binding.dateSwiper.getHighlightedValue().observe(this, date -> {
                if (timePicker != null) {
                    ensureSelectedTimeValid(timePicker.getSelectedTime());
                }
            });
        }

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle arguments = getArguments();
        if (arguments != null) {
            LocalDateTime selected = BundleCompat.getSerializable(arguments, ARG_SELECTED, LocalDateTime.class);
            initTimePickerWithSelected(selected != null ? selected.toLocalTime() : null, savedInstanceState);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        timePicker = null;
    }

    private void initTimePickerWithSelected(LocalTime time, Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.fcvTimeKeypadContainer, KeypadTimePickerFragment.class, new KeypadTimePickerFragment.Builder()
                            .setSelected(time)
                            .buildArguments())
                    .commit();
        }
    }

    private void onTimeSelected(LocalTime time) {
        if (!ensureSelectedTimeValid(time)) {
            return;
        }

        LocalDate date = binding.dateSwiper.getHighlightedValue().getValue();
        LocalDateTime result = LocalDateTime.of(date, time);
        if (getMinimum != null) {
            LocalDateTime minimum = getMinimum.get();
            if (result.isBefore(minimum)) {
                result = minimum;
            }
        }
        setResultOk(result);
        dismiss();
    }

    private boolean ensureSelectedTimeValid(LocalTime selectedTime) {
        if (getMinimum == null || timePicker == null || selectedTime == null) {
            return true;
        }
        LocalDate currentDate = binding.dateSwiper.getHighlightedValue().getValue();
        LocalDateTime minimum = getMinimum.get().truncatedTo(ChronoUnit.MINUTES); //resolution of the time picker
        if (Objects.equals(currentDate, minimum.toLocalDate())) {
            LocalTime minTime = minimum.toLocalTime();
            if (selectedTime.isBefore(minTime)) {
                timePicker.setSelectedTime(minTime);

                if (Objects.equals(lastExceededTime, selectedTime)) {
                    return false;
                }
                lastExceededTime = selectedTime;
                binding.getRoot().post(() -> {
                    if (lastToast != null) {
                        lastToast.cancel();
                    }
                    lastToast = Toast.makeText(requireContext(), getString(R.string.datetime_picker_min_time_exceeded, DateTimeUtils.formatDateTimeLocalized(minimum)), Toast.LENGTH_SHORT);
                    lastToast.show();
                });
                return false;
            }
        }

        return true;
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        setResultCanceled();
    }

    private void setResultCanceled() {
        getParentFragmentManager().setFragmentResult(getRequestKey(), new Bundle());
    }

    private void setResultOk(LocalDateTime result) {
        Bundle b = new Bundle();
        b.putSerializable(RESULT_KEY, result);
        getParentFragmentManager().setFragmentResult(getRequestKey(), b);
    }

    private String getRequestKey() {
        return requireArguments().getString(ARG_REQUEST_KEY);
    }

    public static LocalDateTime getResult(Bundle result) {
        return BundleCompat.getSerializable(result, RESULT_KEY, LocalDateTime.class);
    }

    /**
     * Builder for {@link DateTimePickerDialog}.
     */
    public static class Builder {

        private final Bundle arguments = new Bundle();

        /**
         * Constructor
         *
         * @param requestKey Request code for identifying the target of a dialog result
         */
        public Builder(String requestKey) {
            arguments.putString(ARG_REQUEST_KEY, requestKey);
        }

        /**
         * Sets the minimum date and time that can be selected.
         * If this constraint is violated, a toast will be shown and the selected date and time will be adjusted.
         *
         * @param minDateTime Minimum date and time
         * @return This builder
         */
        public Builder setMinDateTime(LocalDateTime minDateTime) {
            arguments.remove(ARG_MIN_DATETIME_NOW_OFFSET);
            arguments.putSerializable(ARG_MIN_DATETIME, minDateTime);
            return this;
        }

        public Builder setNowMinDateTime() {
            return setNowMinDateTime(Duration.ZERO);
        }

        /**
         * Sets the minimum date and time that can be selected to the live current date and time plus the specified offset in minutes.
         * The actual minimum date and time will be calculated real-time whenever it is needed.
         * If this constraint is violated, a toast will be shown and the selected date and time will be adjusted.
         *
         * @param withOffset Offset in minutes
         * @return This builder
         */
        public Builder setNowMinDateTime(@NonNull Duration withOffset) {
            arguments.remove(ARG_MIN_DATETIME);
            arguments.putSerializable(ARG_MIN_DATETIME_NOW_OFFSET, withOffset);
            return this;
        }

        /**
         * Set the initially selected date and time. The value must not be before
         * the configured minimum, if one is set.
         *
         * @param selected Selected date and time
         * @return This builder
         */
        public Builder setSelected(LocalDateTime selected) {
            arguments.putSerializable(ARG_SELECTED, selected);
            return this;
        }

        /**
         * Builds the dialog.
         *
         * @return The constructed dialog
         */
        public DateTimePickerDialog build() {
            DateTimePickerDialog dialog = new DateTimePickerDialog();
            dialog.setArguments(arguments);
            return dialog;
        }
    }
}
