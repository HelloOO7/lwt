package cz.spojenka.android.ui.view;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import cz.spojenka.android.system.TimeTickReceiver;
import cz.spojenka.android.ui.helpers.InfiniteViewPagerMediator;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.R;

/**
 * An {@link AbstractSwiper} for selecting a time of day.
 * <p>
 * By default, the selected time is relative to the current time. In this scenario,
 * it will also be automatically advanced every minute, so that the times displayed
 * are in proper relation to the "now" time mark.
 * <p>
 * In case a custom time is selected using a picker, then no automatic updates will occur
 * until the swiper is reset.
 */
public class TimeSwiper extends AbstractSwiper<LocalTime> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private static final LocalTime ACTUAL_NOW_TIME = null;

    private LocalTime referenceTime;
    private LocalTime pivotTime = ACTUAL_NOW_TIME;
    private int step = 30;

    private Picker picker = this::openDefaultTimePicker;

    private final TimeTickReceiver timeTickReceiver = new TimeTickReceiver(() -> {
        if (pivotTime == ACTUAL_NOW_TIME) {
            referenceTime = LocalTime.now();
            reloadViews();
        }
    });

    public TimeSwiper(Context context) {
        super(context);
    }

    public TimeSwiper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TimeSwiper(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TimeSwiper(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        timeTickReceiver.register(getContext());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        timeTickReceiver.unregister(getContext());
    }

    /**
     * Set the {@link Picker} that should be used to select a custom time.
     * The default picker is a {@link MaterialTimePicker}.
     *
     * @param picker The picker to use.
     */
    public void setPicker(Picker picker) {
        this.picker = picker;
    }

    /**
     * Set the step in minutes between time value slots in the swiper.
     * The default step is 30 minutes.
     *
     * @param step The step in minutes.
     */
    public void setStep(int step) {
        if (step != this.step) {
            this.step = step;
            reset();
        }
    }

    @Override
    public int valueToPivot(LocalTime value) {
        if (Objects.equals(value, pivotTime)) {
            return 0;
        }
        return (int) (ChronoUnit.MINUTES.between(referenceTime, value) / step);
    }

    @Override
    public LocalTime pivotToValue(int pivot) {
        if (pivot == 0) {
            //chceme, aby se hodnota "ted" zmenila i pokud necha uzivatel aktivitu otevrenou a uplyne cas
            return pivotTime;
        }
        return referenceTime.plusMinutes((long) pivot * step);
    }

    protected void setupViewPager(ViewPager2 viewPager) {
        referenceTime = LocalTime.now();
        viewPager.setOffscreenPageLimit(4);

        new InfiniteViewPagerMediator(viewPager, new RecyclerView.Adapter<TimeViewHolder>() {
            @NonNull
            @Override
            public TimeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new TimeViewHolder(parent.getContext());
            }

            @Override
            public void onBindViewHolder(@NonNull TimeViewHolder holder, int position) {
                holder.bindTime(pivotToValue(position));
            }

            @Override
            public int getItemCount() {
                return 0; //irrelevant
            }
        }).attach();
    }

    @Override
    public void setValue(LocalTime value) {
        pivotTime = value;
        referenceTime = value == ACTUAL_NOW_TIME ? LocalTime.now() : value;
        super.setValue(value);
        reloadViews();
    }

    /**
     * Open a default time picker dialog.
     * This is implemented using a {@link MaterialTimePicker}.
     * The result will be set as the new value of the swiper.
     *
     * @param selected The initial time to be selected in the picker.
     */
    public void openDefaultTimePicker(LocalTime selected) {
        var timePicker = new MaterialTimePicker.Builder()
                .setHour(selected.getHour())
                .setMinute(selected.getMinute())
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .setTimeFormat(DateFormat.is24HourFormat(getContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .build();

        timePicker.addOnPositiveButtonClickListener(selection -> {
            setValue(LocalTime.of(timePicker.getHour(), timePicker.getMinute()));
        });

        timePicker.show(((AppCompatActivity)ViewUtils.getActivityContext(getContext())).getSupportFragmentManager(), "timePicker");
    }

    private void openTimePicker() {
        var selected = highlightedValue.getValue();
        if (selected == ACTUAL_NOW_TIME) {
            selected = LocalTime.now();
        }

        picker.pick(selected);
    }

    @Override
    public int getLeftButtonIcon() {
        return R.drawable.ic_subtract_24px;
    }

    @Override
    public int getRightButtonIcon() {
        return R.drawable.ic_add_24px;
    }

    private class TimeViewHolder extends RecyclerView.ViewHolder {

        private final TextView timeTextView;

        public TimeViewHolder(Context context) {
            super(new TextView(context));
            timeTextView = (TextView) itemView;
            timeTextView.setGravity(Gravity.CENTER);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            timeTextView.setLayoutParams(lp);
            ViewUtils.enableRipple(timeTextView);
            timeTextView.setOnClickListener(v -> openTimePicker());
            timeTextView.setOnLongClickListener(v -> {
                reset();
                return true;
            });
        }

        public void bindTime(LocalTime time) {
            if (time == ACTUAL_NOW_TIME) {
                timeTextView.setText(R.string.now);
            }
            else {
                timeTextView.setText(time.format(FORMAT));
            }
        }
    }

    /**
     * Interface for implementing a custom time picker.
     */
    public static interface Picker {

        /**
         * Open a time picker dialog.
         * If a time is selected, the picker should call {@link TimeSwiper#setValue(LocalTime)} with the result.
         *
         * @param selected The initial time to be selected in the picker.
         */
        public void pick(LocalTime selected);
    }
}
