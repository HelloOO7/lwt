package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import cz.spojenka.android.ui.helpers.AdapterListObserver;
import cz.spojenka.android.ui.helpers.BasicListAdapter;
import cz.spojenka.android.ui.helpers.SingleViewAdapter;
import cz.spojenka.android.ui.helpers.VerticalSpaceItemDecoration;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.FeaturePrerequisite;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.demoapp.databinding.DeviceListItemBinding;
import cz.spojenka.lwt.demoapp.databinding.DeviceListLoadingBinding;
import cz.spojenka.lwt.demoapp.databinding.DeviceListOfflineBinding;
import cz.spojenka.lwt.demoapp.databinding.DeviceListResizeableBinding;
import cz.spojenka.lwt.util.TextMarkupConverter;

public class DeviceListViewController {

    private static final int VIEW_TYPE_VEHICLE = 1;

    private final RecyclerView recyclerView;
    private final DeviceListViewModel viewModel;
    private final TextMarkupConverter markupConverter;

    private final BasicListAdapter<LwtDevice, TripAdvertisementViewHolder> itemAdapter;

    private SingleViewAdapter loadingAdapter;
    private SingleViewAdapter offlineAdapter;

    private DeviceListLoadingBinding loadingView;
    private DeviceListOfflineBinding offlineView;
    private Consumer<DeviceListOfflineBinding> offlineViewDecorator;

    private LoadingSpinnerDisplayRule loadingDisplayRule = LoadingSpinnerDisplayRule.ALWAYS;
    private boolean onClickEffectEnabled = true;
    private boolean enabled = true;

    private DeviceListViewController(RecyclerView recyclerView, DeviceListViewModel viewModel, Object sentinel) {
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
    }

    public DeviceListViewController(RecyclerView recyclerView, DeviceListViewModel viewModel) {
        this(recyclerView, viewModel, null);
        LayoutInflater inflater = LayoutInflater.from(recyclerView.getContext());
        loadingAdapter = createExtraViewAdapter(() -> {
            loadingView = DeviceListLoadingBinding.inflate(inflater);
            return loadingView;
        });
        offlineAdapter = createExtraViewAdapter(() -> {
            offlineView = DeviceListOfflineBinding.inflate(inflater);
            offlineViewDecorator.accept(offlineView);
            return offlineView;
        });
        updateAllUIVisibility();
    }

    public DeviceListViewController(DeviceListResizeableBinding binding, DeviceListViewModel viewModel) {
        this(binding.rvDeviceList, viewModel, null);
        loadingView = binding.loadingPlaceholder;
        offlineView = binding.offlinePlaceholder;
        updateAllUIVisibility();
    }

    private <V extends ViewBinding> SingleViewAdapter createExtraViewAdapter(Supplier<V> bindingSupplier) {
        return new SingleViewAdapter(() -> {
            View v = bindingSupplier.get().getRoot();
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

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        ViewUtils.forEachViewHolder(recyclerView, TripAdvertisementViewHolder.class, vh -> vh.setEnabled(enabled));
    }

    public void bind(LifecycleOwner lifecycleOwner) {
        Context context = recyclerView.getContext();

        new AdapterListObserver<>(itemAdapter) {

            @Override
            public void onChanged(List<LwtDevice> list) {
                super.onChanged(list);
                updateAllUIVisibility();
            }

            @Override
            public void onInserted(int start, List<LwtDevice> items) {
                super.onInserted(start, items);
                updateAllUIVisibility();
                notifyVisibleLoadingState(false);
            }

            @Override
            public void onRemoved(int start, int count) {
                super.onRemoved(start, count);
                updateAllUIVisibility();
            }
        }.attach(lifecycleOwner, viewModel.getDeviceResults());

        if (loadingView == null) {
            recyclerView.setAdapter(new ConcatAdapter(itemAdapter, loadingAdapter));
        } else {
            // this is a workaround for a crash caused by ConcatAdapter and LayoutTransition being used
            // at the same time - we externalize it outside of the RecyclerView
            recyclerView.setAdapter(itemAdapter);
        }
        recyclerView.addItemDecoration(new VerticalSpaceItemDecoration(context.getResources().getDimensionPixelSize(R.dimen.device_list_item_spacing)));
        ViewUtils.setRecyclerViewChangeAnimationsEnabled(recyclerView, false);

        viewModel.getIsLoading().observe(lifecycleOwner, isLoading -> {
            updateLoadingSpinnerDisplay();
            notifyVisibleLoadingState(isLoading);
        });

        viewModel.getRequiredUserAction().observe(lifecycleOwner, action -> {
            setupUserActionUI(action);
            updateLoadingSpinnerDisplay();
        });
    }

    private void setupUserActionUI(DeviceListViewModel.RequiredUserAction action) {
        if (action == null) {
            setUserActionViewVisibility(View.GONE);
        } else {
            setUserActionViewVisibility(View.VISIBLE);
            offlineViewDecorator = view -> {
                @DrawableRes int icon = R.drawable.ic_sensors_off_24px;
                @StringRes int text;
                Runnable onClick;
                Context context = view.getRoot().getContext();

                switch (action) {
                    case BLUETOOTH_ON -> {
                        icon = R.drawable.ic_bluetooth_disabled_24px;
                        text = R.string.device_list_offline_need_bluetooth;
                        onClick = () -> FeaturePrerequisite.BLUETOOTH_ON.startRemedyActivity(context);
                    }
                    case PERMISSIONS -> {
                        text = R.string.device_list_offline_need_perms;
                        onClick = () -> context.startActivity(LWTPrerequisitesActivity.newIntent(context, LWTPrerequisitesActivity.HW_DEFAULT));
                    }
                    case WIFI_ON -> {
                        icon = R.drawable.ic_wifi_tethering_off_24px;
                        text = R.string.device_list_offline_need_wifi;
                        onClick = () -> FeaturePrerequisite.AWARE_AVAILABLE.startRemedyActivity(context);
                    }
                    default -> throw new AssertionError();
                }

                view.ivOfflineIcon.setImageResource(icon);
                view.tvOfflineText.setText(text);
                view.getRoot().setOnClickListener(v -> onClick.run());
            };
            if (offlineView != null) {
                offlineViewDecorator.accept(offlineView);
            }
        }
    }

    private void setUserActionViewVisibility(int visibility) {
        if (offlineAdapter != null) {
            offlineAdapter.setVisibility(visibility);
        } else {
            offlineView.getRoot().setVisibility(visibility);
        }
    }

    private void setListViewVisibility(int visibility) {
        recyclerView.setVisibility(visibility);
    }

    private void setLoadingViewVisibility(int visibility) {
        if (loadingAdapter != null) {
            loadingAdapter.setVisibility(visibility);
        } else {
            loadingView.getRoot().setVisibility(visibility);
        }
    }

    private void updateLoadingSpinnerDisplay() {
        if (!viewModel.isUserActionRequired()) {
            setLoadingViewVisibility(computeLoadingVisibility());
        } else {
            setLoadingViewVisibility(View.GONE);
        }
    }

    private void updateMainListVisibility() {
        setListViewVisibility(computeMainListVisibility());
    }

    private void updateAllUIVisibility() {
        updateMainListVisibility();
        updateLoadingSpinnerDisplay();
    }

    private int computeMainListVisibility() {
        return itemAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE;
    }

    private int computeLoadingVisibility() {
        if (loadingDisplayRule == LoadingSpinnerDisplayRule.NEVER) {
            return View.GONE;
        }

        if (viewModel.isLoading()) {
            if (loadingDisplayRule == LoadingSpinnerDisplayRule.ALWAYS || viewModel.getDeviceResults().isEmpty()) {
                return View.VISIBLE;
            } else {
                return View.GONE;
            }
        } else {
            return View.GONE;
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
            setEnabled(enabled);
        }

        @Override
        public void bind(LwtDevice item) {
            if (item instanceof LwtDevice.Vehicle v) {
                viewController.bind(v.getAdvData());
            }
        }

        private void setEnabled(boolean enabled) {
            itemView.setEnabled(enabled);
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
