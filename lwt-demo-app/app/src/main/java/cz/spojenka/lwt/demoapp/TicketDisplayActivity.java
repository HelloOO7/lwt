package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.IntentCompat;
import androidx.lifecycle.ViewModelProvider;
import cz.spojenka.android.system.TickNotifier;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.util.DateTimeUtils;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.databinding.ActivityTicketDisplayBinding;
import cz.spojenka.lwt.demoapp.databinding.TicketInfoLineRowBinding;

public class TicketDisplayActivity extends BaseActivity {

    public static final String EXTRA_TICKET = TicketDisplayActivity.class.getName() + ".EXTRA_TICKET";

    private ActivityTicketDisplayBinding binding;
    private TicketDisplayViewModel viewModel;

    private ClockView clockView;
    private TickNotifier qrTicker;

    private TicketData ticketForClock;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketDisplayBinding.inflate(getLayoutInflater());
        setContentView(ViewUtils.wrapInScrollView(binding.getRoot()));

        clockView = new ClockView(binding.tvClock);
        clockView.addClockCallback(this::updateProgressBar);

        EdgeSpinnerDrawable qrLoading = new EdgeSpinnerDrawable(
                ShapeAppearanceModel.builder()
                        .setAllCornerSizes(getResources().getDimension(R.dimen.ticket_qr_frame_corner_dim))
                        .build()
        );
        qrLoading.setTint(getColor(R.color.ticket_display_progress_active));
        qrLoading.setStrokeWidth(ViewUtils.dpToPx(this, 4));
        binding.ivQR.setForeground(qrLoading);

        viewModel = new ViewModelProvider(this).get(TicketDisplayViewModel.class);
        TicketData ticket = Objects.requireNonNull(IntentCompat.getParcelableExtra(getIntent(), EXTRA_TICKET, TicketData.class));
        viewModel.loadTicket(ticket);

        qrTicker = new TickNotifier(this, viewModel::updateQR, 30000);

        viewModel.getQrDrawable().observe(this, drawable -> {
            binding.ivQR.setImageDrawable(drawable);
            binding.ivQR.setForeground(null);
        });

        viewModel.getTicketLiveData().observe(this, ticketData -> {
            binding.llTicketInfoRows.removeAllViews();

            var zones = ticketData.getChosenZones();
            if (zones == null || zones.isEmpty()) {
                zones = ticketData.getZoneOptions();
            }
            if (zones != null && !zones.isEmpty()) {
                addInfoView(R.string.ticket_display_valid_zones, String.join(", ", zones));
            }
            addInfoView(R.string.ticket_display_valid_duration, DateTimeUtils.formatTimeDifferenceMinutes(this, ticketData.getValidityPeriod()));
            addInfoView(R.string.ticket_display_valid_since, DateTimeUtils.formatDateTimeLocalized(ticketData.getValidSince().toLocalDateTime()));
            addInfoView(R.string.ticket_display_valid_until, DateTimeUtils.formatDateTimeLocalized(ticketData.getValidUntil().toLocalDateTime()));

            ticketForClock = ticketData;
            updateProgressBar();
        });
    }

    private final DateTimeUtils.RelativeFormatParams relativeFormatParams = new DateTimeUtils.RelativeFormatParams()
            .withRelativeTimeSmallestUnit(ChronoUnit.SECONDS)
            .withDeclension(DateTimeUtils.DateDeclension.ACCUSATIVE)
            .withWeekdayStyle(DateTimeUtils.WeekdayStyle.NONE);

    private void updateProgressBar() {
        if (ticketForClock != null && ticketForClock.getActivatedAt() != null) {
            Context context = this;
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime act = ticketForClock.getActivatedAt();
            OffsetDateTime from = ticketForClock.getValidSince();
            OffsetDateTime to = ticketForClock.getValidUntil();

            @ColorInt int progressTint;
            @ColorInt int progressTextTint = MaterialColors.getColor(binding.tvRemainingTime, android.R.attr.textColor);
            if (!now.isBefore(from) && !now.isAfter(to)) {
                binding.pbValidity.setVisibility(View.VISIBLE);
                Duration toEndOfValidity = Duration.between(now, to);
                binding.tvRemainingTime.setText(getString(
                        R.string.ticket_display_remaining_format,
                        DateTimeUtils.formatTimeDifference(context, toEndOfValidity, ChronoUnit.SECONDS)
                ));
                boolean warn = toEndOfValidity.compareTo(ticketForClock.getValidityPeriod().dividedBy(10)) <= 0;
                progressTint = getProgressBarTint(true, warn);

                setupProgress(from, now, to);
            } else if (now.isBefore(from)) {
                String validityText = getString(
                        R.string.ticket_display_activates_at_format,
                        DateTimeUtils.formatDateTimePhrase(
                                context,
                                from.toLocalDateTime(),
                                relativeFormatParams
                                        .withUseRelativeTimeOnlyLimit(1)
                                        .withAsSeenFrom(now.toLocalDateTime())
                                        .withUseTimeOnlyCondition(localDateTime -> DateTimeUtils.isTodaySameOffset(localDateTime.atOffset(now.getOffset())))
                        )
                );
                progressTint = getProgressBarTint(false, false);
                progressTextTint = progressTint;
                binding.pbValidity.setVisibility(View.VISIBLE);
                binding.tvRemainingTime.setText(validityText);

                setupProgress(act, now, from);
            } else {
                binding.pbValidity.setVisibility(View.GONE);
                binding.tvRemainingTime.setText(R.string.ticket_display_validity_ended);
                progressTint = context.getColor(R.color.ticket_display_progress_bg);
                progressTextTint = context.getColor(R.color.ticket_display_progress_inactive);
            }
            binding.tvRemainingTime.setTextColor(progressTextTint);
            binding.pbValidity.setProgressTintList(ColorStateList.valueOf(progressTint));
        }
    }

    private void setupProgress(OffsetDateTime start, OffsetDateTime now, OffsetDateTime end) {
        long totalMillis = Duration.between(start, end).toMillis();
        long elapsedMillis = Duration.between(start, now).toMillis();
        elapsedMillis = Math.max(0, Math.min(elapsedMillis, totalMillis));
        binding.pbValidity.setMin(0);
        binding.pbValidity.setMax((int) totalMillis);
        binding.pbValidity.setProgress((int) (totalMillis - elapsedMillis));
    }

    protected @ColorInt int getProgressBarTint(boolean isValid, boolean isWarn) {
        if (isValid) {
            return getColor(isWarn ? R.color.ticket_display_progress_warn : R.color.ticket_display_progress_active);
        } else {
            return getColor(R.color.ticket_display_progress_inactive);
        }
    }

    private void addInfoView(@StringRes int titleRes, String text) {
        TicketInfoLineRowBinding infoRowBinding = TicketInfoLineRowBinding.inflate(getLayoutInflater(), binding.llTicketInfoRows, false);
        infoRowBinding.tvLabel.setText(titleRes);
        infoRowBinding.tvValue.setText(text);
        binding.llTicketInfoRows.addView(infoRowBinding.getRoot());
    }

    @Override
    protected void onResume() {
        super.onResume();
        clockView.register();
        qrTicker.register();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clockView.unregister();
        qrTicker.unregister();
    }

    public static class ClockView {

        private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss");

        private final TextView textView;
        private final TickNotifier tickNotifier;
        private final List<Runnable> callbacks = new ArrayList<>();

        public ClockView(TextView textView) {
            this.textView = textView;
            this.tickNotifier = new TickNotifier(textView.getContext(), this::updateClock, 1000);
        }

        public void addClockCallback(Runnable callback) {
            callbacks.add(callback);
        }

        private void updateClock() {
            textView.setText(CLOCK_FORMAT.format(LocalDateTime.now()));
            for (Runnable callback : callbacks) {
                callback.run();
            }
        }

        public void register() {
            tickNotifier.register();
        }

        public void unregister() {
            tickNotifier.unregister();
        }
    }
}
