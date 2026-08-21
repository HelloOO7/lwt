package cz.spojenka.lwt.demoapp;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.widget.TextViewCompat;
import cz.dpp.praguepublictransport.LitackaUtils;
import cz.dpp.praguepublictransport.etd.LitackaETD;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.util.DateTimeUtils;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.LwtTicketMetadata;
import cz.spojenka.lwt.demoapp.databinding.ActivityTicketInspectionDetailBinding;
import cz.spojenka.lwt.demoapp.databinding.TicketInfoLineRowBinding;

public class TicketInspectionDetailActivity extends BaseActivity {

    public static final String EXTRA_ETD = TicketInspectionDetailActivity.class.getName() + ".EXTRA_ETD";
    public static final String EXTRA_INSPECTION_ZONES = TicketInspectionDetailActivity.class.getName() + ".EXTRA_INSPECTION_ZONES";
    public static final String EXTRA_INSPECTION_TIME = TicketInspectionDetailActivity.class.getName() + ".EXTRA_INSPECTION_TIME";
    public static final String EXTRA_INSPECTION_TK = TicketInspectionDetailActivity.class.getName() + ".EXTRA_INSPECTION_TK";

    private ActivityTicketInspectionDetailBinding binding;

    private TicketDisplayActivity.ClockView clock;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketInspectionDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        clock = new TicketDisplayActivity.ClockView(binding.tvClock);

        String etdString = Objects.requireNonNull(getIntent().getStringExtra(EXTRA_ETD));
        LitackaETD etd = LitackaETD.parse(etdString);

        List<String> inspectionZones = getIntent().getStringArrayListExtra(EXTRA_INSPECTION_ZONES);
        Instant inspectionTime = Instant.ofEpochMilli(getIntent().getLongExtra(EXTRA_INSPECTION_TIME, System.currentTimeMillis()));
        String inspectionTk = getIntent().getStringExtra(EXTRA_INSPECTION_TK);

        OffsetDateTime validSince = OffsetDateTime.parse(etd.getProperty("VS"));
        addTimeInfoViews(R.string.ticket_display_valid_since, validSince, inspectionTime, false);
        OffsetDateTime validUntil = OffsetDateTime.parse(etd.getProperty("VU"));
        addTimeInfoViews(R.string.ticket_display_valid_until, validUntil, inspectionTime, true);
        List<String> zones = LitackaUtils.parseCommaSeparatedList(etd.getProperty("VZ"));
        addInfoView(R.string.ticket_display_valid_zones, String.join(", ", zones));
        if (inspectionZones != null && !inspectionZones.isEmpty()) {
            List<String> checkZones = new ArrayList<>(zones);
            checkZones.retainAll(inspectionZones);
            if (!checkZones.isEmpty()) {
                addInfoView(0, getString(R.string.ticket_inspection_detail_valid_current_zone), R.drawable.ic_check_24px, R.color.delay_ok);
            } else {
                if (inspectionZones.size() == 1) {
                    addInfoView(
                            0,
                            getString(
                                    R.string.ticket_inspection_detail_not_valid_current_zone,
                                    inspectionZones.get(0)
                            ),
                            R.drawable.ic_warning_f_24px,
                            R.color.delay_mid
                    );
                } else {
                    addInfoView(
                            0,
                            getString(
                                    R.string.ticket_inspection_detail_not_valid_current_zones,
                                    String.join(", ", inspectionZones)
                            ),
                            R.drawable.ic_warning_f_24px,
                            R.color.delay_mid
                    );
                }
            }
        }
        String lwtInfoString = etd.getProperty("X-LWT");
        if (lwtInfoString != null) {
            LwtTicketMetadata lwtMetadata = LwtTicketMetadata.parse(lwtInfoString);
            addInfoView(R.string.ticket_inspection_detail_linsp, lwtMetadata.getTripKey());
            if (Objects.equals(inspectionTk, lwtMetadata.getTripKey())) {
                addInfoView(0, getString(R.string.ticket_inspection_detail_from_current_trip), R.drawable.ic_info_f_24px);
            }
        }
    }

    private boolean isShouldWarnTime(OffsetDateTime time, Instant now, boolean warnIfAfter) {
        if (warnIfAfter) {
            return now.isAfter(time.toInstant());
        } else {
            return now.isBefore(time.toInstant());
        }
    }

    private String formatTimeInfoText(OffsetDateTime time, Instant now) {
        return DateTimeUtils.formatDateTimePhrase(
                this,
                time.toLocalDateTime(),
                new DateTimeUtils.RelativeFormatParams()
                        .withAsSeenFrom(LocalDateTime.ofInstant(now, time.getOffset()))
                        .withDeclension(DateTimeUtils.DateDeclension.ACCUSATIVE)
                        .withRelativeTimeSmallestUnit(ChronoUnit.SECONDS)
                        .withUseRelativeTimeOnlyLimit(Integer.MAX_VALUE) // always use relative time
        );
    }

    private @DrawableRes int getTimeInfoIcon(OffsetDateTime time, Instant now, boolean warnIfAfter) {
        if (isShouldWarnTime(time, now, warnIfAfter)) {
            return R.drawable.ic_warning_f_24px;
        } else {
            return R.drawable.ic_info_f_24px;
        }
    }

    private @ColorRes int getTimeInfoColor(OffsetDateTime time, Instant now, boolean warnIfAfter) {
        if (isShouldWarnTime(time, now, warnIfAfter)) {
            return R.color.delay_mid;
        } else {
            return Resources.ID_NULL;
        }
    }

    private void addTimeInfoViews(@StringRes int title, OffsetDateTime time, Instant now, boolean warnIfAfter) {
        addInfoView(title, DateTimeUtils.formatDateTimeLocalized(time.toLocalDateTime()));
        var resultBinding = addInfoView(0, formatTimeInfoText(time, now), getTimeInfoIcon(time, now, warnIfAfter), getTimeInfoColor(time, now, warnIfAfter));
        clock.addClockCallback(() -> {
            Instant newNow = Instant.now();
            updateInfoView(resultBinding, 0, formatTimeInfoText(time, newNow), getTimeInfoIcon(time, newNow, warnIfAfter), getTimeInfoColor(time, newNow, warnIfAfter));
        });
    }

    private void addInfoView(@StringRes int titleRes, String text) {
        addInfoView(titleRes, text, Resources.ID_NULL);
    }

    private void addInfoView(@StringRes int titleRes, String text, @DrawableRes int textIcon) {
        addInfoView(titleRes, text, textIcon, Resources.ID_NULL);
    }

    private TicketInfoLineRowBinding addInfoView(@StringRes int titleRes, String text, @DrawableRes int textIcon, @ColorRes int textColor) {
        TicketInfoLineRowBinding row = TicketInfoLineRowBinding.inflate(getLayoutInflater(), binding.llTicketInfoRows, false);
        updateInfoView(row, titleRes, text, textIcon, textColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (titleRes != Resources.ID_NULL) {
            lp.topMargin = ViewUtils.dpToPx(this, 10);
        }
        binding.llTicketInfoRows.addView(row.getRoot(), lp);
        return row;
    }

    private void updateInfoView(TicketInfoLineRowBinding row, @StringRes int titleRes, String text, @DrawableRes int textIcon, @ColorRes int textColor) {
        if (titleRes != Resources.ID_NULL) {
            row.tvLabel.setText(titleRes);
            row.tvLabel.setVisibility(View.VISIBLE);
        } else {
            row.tvLabel.setVisibility(View.INVISIBLE);
            row.tvValue.setTextSize(16);
            ViewUtils.setBold(row.tvValue, false);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.tvValue.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.weight = 0;
            row.tvValue.setLayoutParams(lp);

            lp = (LinearLayout.LayoutParams) row.tvLabel.getLayoutParams();
            lp.weight = 1;
            row.tvLabel.setLayoutParams(lp);
        }
        row.tvValue.setText(text);
        if (textIcon != Resources.ID_NULL) {
            row.tvValue.setCompoundDrawablesRelativeWithIntrinsicBounds(textIcon, 0, 0, 0);
        } else {
            row.tvValue.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }
        if (textColor != Resources.ID_NULL) {
            int color = getColor(textColor);
            row.tvValue.setTextColor(color);
            TextViewCompat.setCompoundDrawableTintList(row.tvValue, ColorStateList.valueOf(color));
        } else {
            row.tvValue.setTextColor(row.tvLabel.getCurrentTextColor());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        clock.register();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clock.unregister();
    }
}
