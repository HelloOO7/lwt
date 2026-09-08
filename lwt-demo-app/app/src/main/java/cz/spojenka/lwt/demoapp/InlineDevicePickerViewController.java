package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.cardview.widget.CardView;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.demoapp.databinding.DeviceListItemCheckmarkBinding;
import cz.spojenka.lwt.demoapp.databinding.DeviceListItemLoadingBarBinding;
import cz.spojenka.lwt.demoapp.databinding.DeviceListResizeableBinding;
import cz.spojenka.lwt.demoapp.databinding.InlineDevicePickerBinding;
import cz.spojenka.lwt.util.TextMarkupConverter;

public class InlineDevicePickerViewController {

    private final InlineDevicePickerBinding binding;

    private final DeviceListViewController devListUIController;
    private final TripInfoViewController selectedDevUIController;

    private final CardView selectedDeviceView;
    private final View checkmark;
    private final View deviceDataLoadingBar;

    private LwtDevice selectedDevice;

    private Boolean overridePickerVisible;

    public InlineDevicePickerViewController(InlineDevicePickerBinding binding, DeviceListViewModel viewModel) {
        this.binding = binding;
        Context context = binding.getRoot().getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        if (binding.getRoot().getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT) {
            devListUIController = new CustomListUIController(binding.deviceList.rvDeviceList, viewModel);
            binding.deviceList.loadingPlaceholder.getRoot().setVisibility(View.GONE);
        } else {
            devListUIController = new CustomListUIController(binding.deviceList, viewModel);
        }
        selectedDevUIController = new TripInfoViewController(binding.selectedDeviceView, devListUIController.getMarkupConverter());
        selectedDeviceView = binding.selectedDeviceView.getRoot();
        selectedDeviceView.setOnClickListener(v -> processDeviceSelected(null));
        selectedDeviceView.setCardElevation(context.getResources().getDimensionPixelSize(R.dimen.ticket_activation_selected_card_elevation));
        ViewUtils.disableRipple(selectedDeviceView);

        checkmark = DeviceListItemCheckmarkBinding.inflate(inflater, selectedDeviceView, true).getRoot();
        deviceDataLoadingBar = DeviceListItemLoadingBarBinding.inflate(inflater, selectedDeviceView, true).getRoot();

        devListUIController.setLoadingDisplayRule(DeviceListViewController.LoadingSpinnerDisplayRule.WHEN_EMPTY);
        // when we click the item, it is hidden and switched to another view. if the ripple were enabled,
        // it would be visible only partially when toggling between the two views, which looks weird, so we disable it.
        devListUIController.setOnClickEffectEnabled(false);
    }

    public void setEnabled(boolean enabled) {
        selectedDeviceView.setEnabled(enabled);
        devListUIController.setEnabled(enabled);
    }

    public DeviceListViewController getDevListUIController() {
        return devListUIController;
    }

    public TripInfoViewController getSelectedDevUIController() {
        return selectedDevUIController;
    }

    public void bind(LifecycleOwner lifecycleOwner) {
        devListUIController.bind(lifecycleOwner);
    }

    /**
     * Override the currently selected device.
     * This will trigger the same behavior as if the user had selected the device from the list.
     * If the device is already selected, this will do nothing (to prevent infinite
     * recursion if the implementer calls this method from {@link #onDeviceSelected(LwtDevice)}).
     *
     * @param device the device to select, or null to clear selection
     */
    public void overrideSelectedDevice(LwtDevice device) {
        if (device != selectedDevice) {
            processDeviceSelected(device);
        }
    }

    private void processDeviceSelected(LwtDevice device) {
        this.selectedDevice = device;
        if (device != null) {
            setShowLoadingBar(true);
            setCheckmarkVisibility(false);

            binding.getRoot().setClipChildren(true);
            selectedDeviceView.setVisibility(View.VISIBLE);
            if (device instanceof LwtDevice.Vehicle v) {
                selectedDevUIController.bind(v.getAdvData());
            }
        } else {
            binding.getRoot().setClipChildren(false);
            setShowLoadingBar(false);
            selectedDeviceView.setVisibility(View.GONE);
        }
        if (!onDeviceSelected(device)) {
            markDeviceAsConfirmed(true);
        }
        updatePickerVisibility();
    }

    private void updatePickerVisibility() {
        if (overridePickerVisible != null) {
            binding.deviceList.getRoot().setVisibility(overridePickerVisible ? View.VISIBLE : View.GONE);
        } else {
            binding.deviceList.getRoot().setVisibility(selectedDevice == null ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Notification that a device has been selected. The implementer
     * may opt in to return false to keep the loading bar visible and the checkmark hidden,
     * do their own asynchronous operations and then call {@link #markDeviceAsConfirmed(boolean)}
     * to notify the controller that loading has finished.
     *
     * @param device selected device, or null for selection cleared
     * @return true to consume the event and prevent default behavior
     */
    protected boolean onDeviceSelected(LwtDevice device) {
        return false;
    }

    public void setShowLoadingBar(boolean show) {
        deviceDataLoadingBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setCheckmarkVisibility(boolean visible) {
        checkmark.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void markDeviceAsConfirmed(boolean confirmed) {
        setCheckmarkVisibility(confirmed);
        setShowLoadingBar(false);
        updatePickerVisibility();
    }

    /**
     * Set an override on the picker visibility, which, if set,
     * will be used instead of the default visibility logic. This only affects
     * the device list picker, not the actually selected device view.
     *
     * @see #clearOverridePickerVisible()
     *
     * @param overridePickerVisible true/false
     */
    public void setOverridePickerVisible(boolean overridePickerVisible) {
        this.overridePickerVisible = overridePickerVisible;
    }

    public void clearOverridePickerVisible() {
        this.overridePickerVisible = null;
    }

    private class CustomListUIController extends DeviceListViewController {

        public CustomListUIController(RecyclerView recyclerView, DeviceListViewModel viewModel) {
            super(recyclerView, viewModel);
        }

        public CustomListUIController(DeviceListResizeableBinding binding, DeviceListViewModel viewModel) {
            super(binding, viewModel);
        }

        @Override
        protected void onDeviceSelected(LwtDevice device) {
            processDeviceSelected(device);
        }
    }
}
