package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import cz.spojenka.android.ui.helpers.AdapterListObserver;
import cz.spojenka.android.ui.helpers.BasicListAdapter;
import cz.spojenka.android.ui.helpers.SingleViewAdapter;
import cz.spojenka.android.ui.helpers.VerticalSpaceItemDecoration;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.demoapp.databinding.DeviceListItemBinding;
import cz.spojenka.lwt.demoapp.databinding.DeviceListLoadingBinding;
import cz.spojenka.lwt.util.TextMarkupConverter;

public class DeviceListViewController {

    private static final int VIEW_TYPE_VEHICLE = 1;

    private final RecyclerView recyclerView;
    private final DeviceListViewModel viewModel;
    private final TextMarkupConverter markupConverter;

    private final BasicListAdapter<LwtDevice, TripAdvertisementViewHolder> itemAdapter;
    private final SingleViewAdapter loadingAdapter;

    private LoadingSpinnerDisplayRule loadingDisplayRule = LoadingSpinnerDisplayRule.ALWAYS;
    private boolean onClickEffectEnabled = true;

    public DeviceListViewController(RecyclerView recyclerView, DeviceListViewModel viewModel) {
        this.recyclerView = recyclerView;
        this.viewModel = viewModel;
        markupConverter = new TextMarkupConverter(recyclerView.getContext().getResources().getFont(R.font.ropid_piktogramy));

        LayoutInflater inflater = LayoutInflater.from(recyclerView.getContext());

        itemAdapter = new BasicListAdapter<>() {

            @NonNull
            @Override
            public TripAdvertisementViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new TripAdvertisementViewHolder(DeviceListItemBinding.inflate(inflater, parent, false));
            }

            @Override
            public int getItemViewType(int position) {
                // currently only vehicles
                return VIEW_TYPE_VEHICLE;
            }

            @Override
            protected View.OnClickListener onBindItemClickListener(LwtDevice item) {
                return v -> onDeviceSelected(item);
            }
        };
        loadingAdapter = new SingleViewAdapter(() -> {
            View v = DeviceListLoadingBinding.inflate(inflater).getRoot();
            v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return v;
        });
    }

    public TextMarkupConverter getMarkupConverter() {
        return markupConverter;
    }

    public void setLoadingDisplayRule(LoadingSpinnerDisplayRule loadingDisplayRule) {
        this.loadingDisplayRule = loadingDisplayRule;
        updateLoadingSpinnerDisplay();
    }

    public void setOnClickEffectEnabled(boolean onClickEffectEnabled) {
        this.onClickEffectEnabled = onClickEffectEnabled;
        ViewUtils.forEachViewHolder(recyclerView, TripAdvertisementViewHolder.class, vh -> vh.setRippleEffectEnabled(onClickEffectEnabled));
    }

    public void bind(LifecycleOwner lifecycleOwner) {
        Context context = recyclerView.getContext();

        new AdapterListObserver<>(itemAdapter) {

            @Override
            public void onChanged(List<LwtDevice> list) {
                super.onChanged(list);
                updateLoadingSpinnerDisplay();
            }

            @Override
            public void onInserted(int start, List<LwtDevice> items) {
                super.onInserted(start, items);
                updateLoadingSpinnerDisplay();
                notifyVisibleLoadingState(false);
            }

            @Override
            public void onRemoved(int start, int count) {
                super.onRemoved(start, count);
                updateLoadingSpinnerDisplay();
            }
        }.attach(lifecycleOwner, viewModel.getDeviceResults());

        recyclerView.setAdapter(new ConcatAdapter(itemAdapter, loadingAdapter));
        recyclerView.addItemDecoration(new VerticalSpaceItemDecoration(context.getResources().getDimensionPixelSize(R.dimen.device_list_item_spacing)));
        ViewUtils.setRecyclerViewChangeAnimationsEnabled(recyclerView, false);

        viewModel.getIsLoading().observe(lifecycleOwner, isLoading -> {
            updateLoadingSpinnerDisplay();
            notifyVisibleLoadingState(isLoading);
        });
    }

    private void updateLoadingSpinnerDisplay() {
        if (loadingDisplayRule == LoadingSpinnerDisplayRule.NEVER) {
            loadingAdapter.setVisibility(View.GONE);
            return;
        }

        if (viewModel.isLoading()) {
            if (loadingDisplayRule == LoadingSpinnerDisplayRule.ALWAYS || viewModel.getDeviceResults().isEmpty()) {
                loadingAdapter.setVisibility(View.VISIBLE);
            } else {
                loadingAdapter.setVisibility(View.GONE);
            }
        } else {
            loadingAdapter.setVisibility(View.GONE);
        }
    }

    protected void notifyVisibleLoadingState(boolean isLoading) {

    }

    protected void onDeviceSelected(LwtDevice device) {

    }

    private class TripAdvertisementViewHolder extends BasicListAdapter.BasicViewHolder<LwtDevice> {

        private final TripInfoViewController viewController;

        public TripAdvertisementViewHolder(DeviceListItemBinding binding) {
            super(binding.getRoot());
            this.viewController = new TripInfoViewController(binding, markupConverter);
            setRippleEffectEnabled(onClickEffectEnabled);
        }

        @Override
        public void bind(LwtDevice item) {
            if (item instanceof LwtDevice.Vehicle v) {
                viewController.bind(v.getAdvData());
            }
        }

        private void setRippleEffectEnabled(boolean enabled) {
            if (enabled) {
                ViewUtils.enableRipple(itemView);
            } else {
                ViewUtils.disableRipple(itemView);
            }
        }
    }

    public static enum LoadingSpinnerDisplayRule {
        NEVER,
        ALWAYS,
        WHEN_EMPTY
    }
}
