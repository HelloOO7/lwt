package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.helpers.AdapterListObserver;
import cz.spojenka.android.ui.helpers.BasicListAdapter;
import cz.spojenka.android.ui.helpers.SingleViewAdapter;
import cz.spojenka.android.ui.helpers.VerticalSpaceItemDecoration;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.TripAdvertisementData;
import cz.spojenka.lwt.TripAdvertisementDataExt;
import cz.spojenka.lwt.demoapp.databinding.ActivityDeviceListBinding;
import cz.spojenka.lwt.demoapp.databinding.DeviceListItemBinding;
import cz.spojenka.lwt.demoapp.databinding.DeviceListLoadingBinding;
import cz.spojenka.lwt.util.TextMarkupConverter;

public class DeviceListActivity extends BaseActivity {

    private static final int VIEW_TYPE_VEHICLE = 1;

    private ActivityDeviceListBinding binding;
    private DeviceListViewModel viewModel;

    private Typeface iconFont;
    private TextMarkupConverter markupConverter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDeviceListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(DeviceListViewModel.class);
        iconFont = getResources().getFont(R.font.ropid_piktogramy);
        markupConverter = new TextMarkupConverter(iconFont);

        var itemAdapter = new BasicListAdapter<LwtDevice, TripAdvertisementViewHolder>() {

            @NonNull
            @Override
            public TripAdvertisementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new TripAdvertisementViewHolder(DeviceListItemBinding.inflate(getLayoutInflater(), parent, false));
            }

            @Override
            public int getItemViewType(int position) {
                // currently only vehicles
                return VIEW_TYPE_VEHICLE;
            }
        };
        new AdapterListObserver<>(itemAdapter) {
            @Override
            public void onInserted(int start, List<LwtDevice> items) {
                super.onInserted(start, items);
                binding.getRoot().setRefreshing(false);
            }
        }.attach(this, viewModel.getDeviceResults());

        var stillLoadingAdapter = new SingleViewAdapter(() -> {
            View v = DeviceListLoadingBinding.inflate(getLayoutInflater()).getRoot();
            v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return v;
        }) {

            @Override
            public int getItemCount() {
                return viewModel.isLoading() ? 1 : 0;
            }
        };

        binding.rvDeviceList.setAdapter(new ConcatAdapter(itemAdapter, stillLoadingAdapter));
        binding.rvDeviceList.addItemDecoration(new VerticalSpaceItemDecoration(getResources().getDimensionPixelSize(R.dimen.device_list_item_spacing)));
        ViewUtils.setRecyclerViewChangeAnimationsEnabled(binding.rvDeviceList, false);

        viewModel.getIsLoading().observe(this, isLoading -> {
            stillLoadingAdapter.notifyDataSetChanged();
            if (!isLoading) {
                binding.getRoot().setRefreshing(false);
            }
        });

        viewModel.getScanError().observe(this, error -> {
            if (error != null) {
                binding.getRoot().setRefreshing(false);

                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.error_comm)
                        .setMessage(error.getMessage())
                        .setPositiveButton(android.R.string.ok, (dlg, which) -> finish())
                        .setCancelable(false);

                viewModel.ackScanError();
            }
        });

        binding.getRoot().setOnRefreshListener(viewModel::reload);
        int srlEnd = binding.getRoot().getProgressViewEndOffset();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            int offset = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()).top - binding.getRoot().getProgressCircleDiameter();
            binding.getRoot().setProgressViewOffset(
                    getSupportActionBar() == null,
                    offset,
                    offset + srlEnd
            );
            return insets;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewModel.cancel();
    }

    private class TripAdvertisementViewHolder extends BasicListAdapter.BasicViewHolder<LwtDevice> {

        private final DeviceListItemBinding binding;

        public TripAdvertisementViewHolder(DeviceListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private Context getContext() {
            return binding.getRoot().getContext();
        }

        @Override
        public void bind(LwtDevice item) {
            if (item instanceof LwtDevice.Vehicle v) {
                bindVehicle(v);
            }
        }

        private Spanned parseHtml(String html) {
            return markupConverter.toSpannableString(html);
        }

        private void bindVehicle(LwtDevice.Vehicle v) {
            TripAdvertisementData d = v.getAdvData();
            CharSequence stopName;
            if (d instanceof TripAdvertisementDataExt ext) {
                Spanned lineNum = parseHtml(ext.getLineName());
                binding.tvLineNumber.setText(lineNum);
                binding.tvHeadsign.setText(parseHtml(ext.getHeadsign()));
                BackgroundColorSpan lineBgColor = markupConverter.extractBackgroundColor(lineNum);
                if (lineBgColor != null) {
                    binding.tvLineNumber.setBackgroundTintList(ColorStateList.valueOf(lineBgColor.getBackgroundColor()));
                } else {
                    binding.tvLineNumber.setBackground(null);
                }
                stopName = parseHtml(ext.getCurrentStopName());
            } else {
                if (d.isTrain()) {
                    binding.tvLineNumber.setText(d.getParsedTrainLineNumber());
                } else {
                    binding.tvLineNumber.setText(String.valueOf(d.getLineLicenseNumber() % 1000));
                }
                binding.tvLineNumber.setBackground(null);
                binding.tvHeadsign.setText(String.valueOf(d.getDirectionCisNumber()));
                stopName = String.valueOf(d.getStopCisNumber());
            }
            if (d.isAtStop()) {
                binding.tvNextStop.setText(TextUtils.concat(getContext().getString(R.string.vehicle_at_stop_prefix), stopName));
            } else {
                binding.tvNextStop.setText(TextUtils.concat(getContext().getString(R.string.vehicle_next_stop_prefix), stopName));
            }
            setDelayDisplay(d.getDelay());
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
}
