package cz.spojenka.android.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import cz.spojenka.android.polyfills.LocalDateCompat;
import cz.spojenka.android.system.DateChangedReceiver;
import cz.spojenka.android.ui.helpers.InfiniteViewPagerMediator;
import cz.spojenka.android.util.DateTimeUtils;
import cz.spojenka.android.util.ViewUtils;

/**
 * A {@link AbstractSwiper} for selecting dates.
 * <p>
 * The swiper automatically updates on midnight transitions and also allows setting a minimum date
 * that can be selected.
 */
public class DateSwiper extends AbstractSwiper<LocalDate> {

    private LocalDate minimumDate = null;
    private LocalDate pivotDate;
    private LocalDate referenceDate;
    private final DateChangedReceiver dateChangedReceiver = new DateChangedReceiver(() -> {
        if (isShowingPivotDate()) {
            updatePivotDate();
        } else {
            updateReferenceDate();
            if (isShowingReferenceDate()) {
                //if we were originally showing "tomorrow" and the the date changed, and we are thus showing "today",
                //we shall make it the pivot date
                updatePivotDate();
            }
        }
        reloadViews();
    });

    public DateSwiper(Context context) {
        super(context);
    }

    public DateSwiper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DateSwiper(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DateSwiper(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        dateChangedReceiver.register(getContext());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dateChangedReceiver.unregister(getContext());
    }

    /**
     * Set the minimum date that can be selected. If the currently selected date is before the minimum date,
     * the minimum date will be selected instead.
     *
     * @param date The minimum date, or null to remove the minimum date.
     */
    public void setMinimumDate(LocalDate date) {
        LocalDate current = getHighlightedValue().getValue();
        minimumDate = date;
        reloadViews();
        if (minimumDate != null) {
            if (current != null && !current.isBefore(minimumDate)) {
                setValue(current);
            } else {
                setValue(minimumDate);
            }
        }
    }

    private long getDateMillis(LocalDate date) {
        return ZonedDateTime.of(date, LocalTime.NOON, ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Open a date picker dialog to select the current date.
     * This may be overridden to provide a custom date picker - in that case, it should call {@link #setValue(LocalDate)}
     * with the result.
     */
    protected void openDatePicker() {
        var builder = MaterialDatePicker.Builder.datePicker();

        if (minimumDate != null) {
            builder.setCalendarConstraints(new CalendarConstraints.Builder()
                    .setValidator(DateValidatorPointForward.from(getDateMillis(minimumDate)))
                    .build());
        }

        MaterialDatePicker<Long> datePicker = builder
                .setSelection(getDateMillis(highlightedValue.getValue()))
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            setValue(LocalDateCompat.ofInstant(Instant.ofEpochMilli(selection), ZoneId.systemDefault()));
        });

        datePicker.show(((AppCompatActivity) ViewUtils.getActivityContext(getContext())).getSupportFragmentManager(), "datePicker");
    }

    private void checkDateInRange(LocalDate date) {
        if (minimumDate != null && date.isBefore(minimumDate)) {
            throw new IllegalArgumentException(date + " is before minimum date " + minimumDate);
        }
    }

    @Override
    public void setValue(LocalDate value) {
        checkDateInRange(value);
        boolean shouldReloadViews = false;
        if (value == null) {
            if (updatePivotDate()) {
                shouldReloadViews = true;
            }
            value = pivotDate;
        }
        super.setValue(value);
        if (shouldReloadViews) {
            reloadViews();
        }
    }

    @Override
    public int valueToPivot(LocalDate value) {
        if (value == null) {
            return 0;
        }
        if (minimumDate != null) {
            checkDateInRange(value);
            int days = (int) ChronoUnit.DAYS.between(minimumDate, value);
            return InfiniteViewPagerMediator.minPivot() + days;
        }
        return (int) ChronoUnit.DAYS.between(pivotDate, value);
    }

    @Override
    public LocalDate pivotToValue(int pivot) {
        if (minimumDate != null) {
            return minimumDate.plusDays(pivot - InfiniteViewPagerMediator.minPivot());
        }
        return pivotDate.plusDays(pivot);
    }

    private boolean isShowingPivotDate() {
        return Objects.equals(pivotDate, highlightedValue.getValue());
    }

    private boolean isShowingReferenceDate() {
        return Objects.equals(pivotDate, highlightedValue.getValue());
    }

    private boolean updatePivotDate() {
        boolean wasShowing = isShowingPivotDate();
        LocalDate newPivotDate = LocalDate.now();
        if (Objects.equals(newPivotDate, pivotDate)) {
            return false;
        }
        pivotDate = newPivotDate;
        if (!highlightedValue.isInitialized() || wasShowing) {
            highlightedValue.setValue(pivotDate);
        }
        updateReferenceDate();
        return true;
    }

    private void updateReferenceDate() {
        referenceDate = LocalDate.now();
    }

    @Override
    protected void setupViewPager(ViewPager2 viewPager) {
        updatePivotDate();
        viewPager.setOffscreenPageLimit(4);

        new InfiniteViewPagerMediator(viewPager, new RecyclerView.Adapter<DateViewHolder>() {
            @NonNull
            @Override
            public DateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new DateViewHolder(parent.getContext());
            }

            @Override
            public void onBindViewHolder(@NonNull DateViewHolder holder, int position) {
                holder.bindDate(pivotToValue(position));
            }

            @Override
            public int getItemCount() {
                return 0; //irrelevant
            }
        }).attach();
    }

    private class DateViewHolder extends RecyclerView.ViewHolder {

        private final TextView dateTextView;

        public DateViewHolder(Context context) {
            super(new TextView(context));
            dateTextView = (TextView) itemView;
            dateTextView.setGravity(Gravity.CENTER);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dateTextView.setLayoutParams(lp);
            ViewUtils.enableRipple(dateTextView);
            dateTextView.setOnClickListener(v -> openDatePicker());
            dateTextView.setOnLongClickListener(v -> {
                reset();
                return true;
            });
        }

        public void bindDate(LocalDate date) {
            dateTextView.setText(DateTimeUtils.formatRelativeDate(getContext(), date, referenceDate, DateTimeUtils.WeekdayStyle.SHORT));
        }
    }
}
