package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;

import androidx.annotation.ColorRes;
import androidx.core.widget.TextViewCompat;
import cz.spojenka.lwt.LocationState;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.TripAdvertisementData;
import cz.spojenka.lwt.TripAdvertisementDataExt;
import cz.spojenka.lwt.TripStateInfo;
import cz.spojenka.lwt.demoapp.databinding.DeviceListItemBinding;
import cz.spojenka.lwt.util.TextMarkupConverter;

public class TripInfoViewController {

    private final DeviceListItemBinding binding;
    private final TextMarkupConverter markupConverter;

    public TripInfoViewController(DeviceListItemBinding binding, TextMarkupConverter markupConverter) {
        this.binding = binding;
        this.markupConverter = markupConverter;
    }

    private Context getContext() {
        return binding.getRoot().getContext();
    }

    private Spanned parseHtml(String html) {
        return markupConverter.toSpannableString(html);
    }

    public void bind(TripAdvertisementData d) {
        String stopName;
        if (d instanceof TripAdvertisementDataExt ext) {
            showLineNumber(ext.getLineName());
            showHeadsign(ext.getHeadsign());
            stopName = ext.getCurrentStopName();
        } else {
            if (d.isTrain()) {
                binding.tvLineNumber.setText(d.getParsedTrainLineNumber());
            } else {
                binding.tvLineNumber.setText(String.valueOf(d.getLineLicenseNumber() % 1000));
            }
            binding.tvLineNumber.setBackground(null);
            showHeadsign(String.valueOf(d.getDirectionCisNumber()));
            stopName = String.valueOf(d.getStopCisNumber());
        }
        showNextStop(stopName, d.isAtStop());
        setDelayDisplay(d.getDelay());
    }

    public void bind(TripStateInfo tripInfo) {
        showLineNumber(tripInfo.trip().line().name());
        showHeadsign(tripInfo.trip().line().headsign().name());
        showNextStop(tripInfo.currentDepartureStop().name(), tripInfo.locationState() == LocationState.AtStop);
        setDelayDisplay(tripInfo.delay());
    }

    private void showLineNumber(String lineNumberHtml) {
        Spanned lineNum = parseHtml(lineNumberHtml);
        binding.tvLineNumber.setText(lineNum);
        BackgroundColorSpan lineBgColor = markupConverter.extractBackgroundColor(lineNum);
        if (lineBgColor != null) {
            binding.tvLineNumber.setBackgroundTintList(ColorStateList.valueOf(lineBgColor.getBackgroundColor()));
        } else {
            binding.tvLineNumber.setBackground(null);
        }
    }

    private void showHeadsign(String headsignHtml) {
        binding.tvHeadsign.setText(parseHtml(headsignHtml));
    }

    private void showNextStop(String stopNameHtml, boolean isAtStop) {
        Spanned stopName = parseHtml(stopNameHtml);
        if (isAtStop) {
            binding.tvNextStop.setText(TextUtils.concat(getContext().getString(R.string.vehicle_at_stop_prefix), stopName));
        } else {
            binding.tvNextStop.setText(TextUtils.concat(getContext().getString(R.string.vehicle_next_stop_prefix), stopName));
        }
    }

    private int getDelayColor(int delay) {
        @ColorRes int resId;
        if (delay < 5) {
            resId = R.color.delay_ok;
        } else if (delay < 10) {
            resId = R.color.delay_mid;
        } else {
            resId = R.color.delay_high;
        }
        return getContext().getColor(resId);
    }

    private void setDelayDisplay(int delay) {
        TextViewCompat.setCompoundDrawableTintList(binding.tvDelayDisplay, ColorStateList.valueOf(getDelayColor(delay)));
        if (delay <= 0) {
            binding.tvDelayDisplay.setText(R.string.delay_on_time);
        } else {
            binding.tvDelayDisplay.setText(getContext().getString(R.string.delay_minutes_format, delay));
        }
    }
}
