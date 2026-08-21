package cz.spojenka.lwt.demoapp;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.core.content.IntentCompat;
import androidx.lifecycle.ViewModelProvider;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.dialog.CommonDialogs;
import cz.spojenka.android.ui.dialog.DateTimePickerDialog;
import cz.spojenka.android.ui.resources.ListFormat;
import cz.spojenka.android.ui.view.ConnectionRouteNode;
import cz.spojenka.android.util.DateTimeUtils;
import cz.spojenka.android.util.LiveDataUtils;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.LwtDevice;
import cz.spojenka.lwt.TripRouteInfo;
import cz.spojenka.lwt.TripStopInfo;
import cz.spojenka.lwt.demoapp.databinding.ActivityTicketActivationBinding;
import cz.spojenka.lwt.demoapp.databinding.DeviceListItemCheckmarkBinding;
import cz.spojenka.lwt.demoapp.databinding.DeviceListItemLoadingBarBinding;
import cz.spojenka.lwt.util.TextMarkupConverter;

public class TicketActivationActivity extends BaseActivity {

    private static final String TAG = TicketActivationActivity.class.getSimpleName();

    public static final String EXTRA_TICKET_ID = TicketActivationActivity.class.getName() + ".EXTRA_TICKET_ID";
    public static final String EXTRA_TICKET = TicketActivationActivity.class.getName() + ".EXTRA_TICKET";
    public static final String EXTRA_PREPAID_ZONES = TicketActivationActivity.class.getName() + ".EXTRA_PREPAID_ZONES";

    private static final String REQUEST_KEY_ACTIVATION_TIME = TicketActivationActivity.class.getName() + ".REQUEST_KEY_ACTIVATION_TIME";

    private ActivityTicketActivationBinding binding;

    private DeviceListViewController devListUIController;
    private TripInfoViewController selectedDevUIController;
    private ActivityResultLauncher<LitackaZonePickerActivity.Input> zonePickerLauncher;
    private ActivityResultLauncher<TripStopPickerActivity.Input> stopPickerLauncher;

    private TicketActivationViewModel viewModel;
    private DeviceListViewModel devicesViewModel;

    private PillPopoutAnimation currentFastPillAnim;

    private EdgeSpinnerDrawable activationSpinner;
    private ViewUtils.ViewEnabledSaveState enabledStateBeforeActivation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTicketActivationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(TicketActivationViewModel.class);
        devicesViewModel = viewModel.getDevicesViewModel();

        devListUIController = new DeviceListViewController(binding.rvDeviceList, devicesViewModel) {

            @Override
            protected void onDeviceSelected(LwtDevice device) {
                viewModel.selectAutoActivationDevice(device);
            }
        };
        devListUIController.setLoadingDisplayRule(DeviceListViewController.LoadingSpinnerDisplayRule.WHEN_EMPTY);
        // when we click the item, it is hidden and switched to another view. if the ripple were enabled,
        // it would be visible only partially when toggling between the two views, which looks weird, so we disable it.
        devListUIController.setOnClickEffectEnabled(false);

        selectedDevUIController = new TripInfoViewController(binding.selectedDeviceView, devListUIController.getMarkupConverter());
        binding.selectedDeviceView.getRoot().setOnClickListener(v -> viewModel.selectAutoActivationDevice(null));

        zonePickerLauncher = registerForActivityResult(LitackaZonePickerActivity.PICK_ZONES, result -> {
            if (result != null) {
                viewModel.setChosenZones(result);
            }
        });
        stopPickerLauncher = registerForActivityResult(TripStopPickerActivity.PICK_STOP, result -> {
            TripRouteInfo route = viewModel.getSelectedRouteInfo();
            if (result >= 0 && route != null) {
                viewModel.setActivationStop(route.stops(result).stopRef());
            }
        });

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_TICKET_ID)) {
            // not implemented in demo app
        } else {
            TicketData ticket = Objects.requireNonNull(IntentCompat.getParcelableExtra(intent, EXTRA_TICKET, TicketData.class));

            viewModel.setTicket(ticket);

            String[] prepaid = intent.getStringArrayExtra(EXTRA_PREPAID_ZONES);
            if (prepaid != null) {
                viewModel.setPrepaidZones(List.of(prepaid));
            }
        }

        devListUIController.bind(this);

        binding.llZoneChoiceType.setOnClickListener(v -> showZoneChoiceTypeDialog());

        viewModel.getChosenZones().observe(this, zones -> {
            if (zones.isManual()) {
                binding.tvZoneChoiceSummary.setText(getString(R.string.ticket_activation_zone_choice_summary_manual, String.join(", ", zones.zones())));
            } else {
                binding.tvZoneChoiceSummary.setText(R.string.ticket_activation_zone_choice_summary_auto);
                if (viewModel.isCanNotUseTicketWithDevice()) {
                    viewModel.discardAutoActivationDeviceFull();
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.ticket_activation_incompatible_line_ticket_title)
                            .setMessage(R.string.ticket_activation_incompatible_line_ticket_message)
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                }
            }
            updateDeviceChoiceVisibility();
        });

        binding.llContent.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
        binding.llHeader.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
        binding.getRoot().getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);

        binding.llActivationTime.setOnClickListener(v -> showActivationTimeTypeDialog());

        getSupportFragmentManager().setFragmentResultListener(REQUEST_KEY_ACTIVATION_TIME, this, (requestKey, result) -> {
            LocalDateTime time = DateTimePickerDialog.getResult(result);
            if (time != null) {
                viewModel.setActivationTime(time);
            }
        });

        viewModel.getActivationTime().observe(this, activationTime -> {
            if (activationTime.isNow()) {
                binding.tvActivationTimeSummary.setText(R.string.ticket_activation_activation_time_summary_immediately);
            } else if (!activationTime.isManual() && viewModel.hasActivationStop()) {
                binding.tvActivationTimeSummary.setText(R.string.ticket_activation_activation_time_summary_default);
            } else {
                binding.tvActivationTimeSummary.setText(DateTimeUtils.formatDateTimeLocalized(activationTime.time()));
            }
        });

        binding.selectedDeviceView.getRoot().setCardElevation(getResources().getDimensionPixelSize(R.dimen.ticket_activation_selected_card_elevation));
        ViewUtils.disableRipple(binding.selectedDeviceView.getRoot());
        View checkmark = DeviceListItemCheckmarkBinding.inflate(getLayoutInflater(), binding.selectedDeviceView.getRoot(), true).getRoot();
        View deviceDataLoadingBar = DeviceListItemLoadingBarBinding.inflate(getLayoutInflater(), binding.selectedDeviceView.getRoot(), true).getRoot();

        viewModel.getSelectedAutoActivationDevice().observe(this, device -> {
            if (device != null) {
                binding.selectedDeviceView.getRoot().setVisibility(View.VISIBLE);
                if (device instanceof LwtDevice.Vehicle v) {
                    selectedDevUIController.bind(v.getAdvData());
                }
            } else {
                binding.selectedDeviceView.getRoot().setVisibility(View.GONE);
            }
            updateDeviceChoiceVisibility();
        });

        viewModel.getDeviceDataIsLoading().observe(this, loading -> {
            deviceDataLoadingBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            updateDeviceCheckmark(checkmark);
        });

        viewModel.getActivationStop().observe(this, activationStop -> {
            if (activationStop != null) {
                LocalDateTime time = viewModel.getActivationStopDepTime();
                binding.activationStop.tvArrivalTime.setVisibility(View.GONE);
                if (time != null) {
                    binding.activationStop.tvDepartureTime.setText(DateTimeUtils.formatTime(time.toLocalTime()));
                }
                CharSequence stopName = devListUIController.getMarkupConverter().toSpannableString(activationStop.name());
                stopName = TextUtils.concat(getText(R.string.ticket_activation_depart_from_title), "\n", stopName);
                binding.activationStop.tvStationName.setText(stopName);
                setupTariffZoneViews(viewModel.getActivationStopExt());

                var node = binding.activationStop.connectionRouteNode;
                node.setPathTypeOut(ConnectionRouteNode.PathType.SELECTED);
                if (activationStop.sequenceId() > 0) {
                    node.setShowPathIn(true);
                    node.setPathTypeIn(ConnectionRouteNode.PathType.NORMAL);
                    node.setNodeType(ConnectionRouteNode.NodeType.NORMAL);
                    node.setNodeCurrent(true);
                } else {
                    node.setShowPathIn(false);
                    node.setNodeType(ConnectionRouteNode.NodeType.START);
                    node.setNodeCurrent(false);
                }
            }

            updateDeviceChoiceVisibility();
        });

        binding.activationStop.getRoot().setOnClickListener(v -> launchStopPicker());

        viewModel.getRawServerAuthenticationResult().observe(this, trusted -> {
            updateDeviceCheckmark(checkmark);
            if (trusted == null) {
                // no device
                return;
            }

            LwtDevice device = viewModel.getCurrentAutoActivationDevice();
            if (!trusted && device != null) {
                if (DebugFlags.isAllowUntrustedCertificates()) {
                    Log.e(TAG, "Untrusted server certificate for device " + device.getAddress() + ", but allowing due to debug flag");
                    Toast.makeText(this, R.string.untrusted_debug_toast, Toast.LENGTH_LONG).show();
                } else {
                    viewModel.discardAutoActivationDeviceFull();
                    new MaterialAlertDialogBuilder(this)
                            .setView(R.layout.dialog_untrusted_server)
                            .setCancelable(false)
                            .setPositiveButton(R.string.untrusted_dialog_dismiss, (dialog, which) -> {
                                devicesViewModel.hideDevicesWithAddress(device.getAddress());
                            })
                            .show();
                }
            }
        });

        LiveDataUtils.combine(() -> {
            String startText = formatValidityStartText();
            String endText = formatValidityEndText();
            if (startText == null) {
                return getString(R.string.ticket_activation_summary_initial);
            } else if (endText == null) {
                return startText;
            } else {
                return startText + " " + endText;
            }
        }, viewModel.getActivationTime(), viewModel.getActivationStop(), viewModel.getValidityEndStop(), viewModel.getChosenZones()).observe(this, binding.tvActivationSummary::setText);

        viewModel.getCanActivateTicket().observe(this, binding.fabConfirm::setEnabled);

        binding.fabConfirm.setOnClickListener(v -> showTicketActivationConfirmation());

        viewModel.getPreauthExpirationSecondsLeft().observe(this, secondsLeft -> fastActivationCountdownPillUpdater.run());

        binding.tvFastActivationTimer.setOnClickListener(v -> {
            CommonDialogs.newInfoDialog(this, R.string.fast_activation_infobox_title, R.string.fast_activation_infobox_message)
                    .show();
        });

        activationSpinner = new EdgeSpinnerDrawable(binding.fabConfirm.getShapeAppearanceModel());
        activationSpinner.setTintList(binding.fabConfirm.getTextColors());
        activationSpinner.setDuration(1500);

        Drawable defaultBtnActivateIcon = binding.fabConfirm.getIcon();

        viewModel.getIsActivationInProgress().observe(this, inProgress -> {
            if (inProgress) {
                if (enabledStateBeforeActivation == null) {
                    enabledStateBeforeActivation = ViewUtils.setViewsEnabledRecursive(binding.llActivationFormWrapper, false);
                }
                binding.fabConfirm.setText(R.string.ticket_activation_confirm_in_progress);
                binding.fabConfirm.setIcon(null);
                binding.fabConfirm.setForeground(activationSpinner);
                binding.fabConfirm.setClickable(false);
            } else {
                if (enabledStateBeforeActivation != null) {
                    ViewUtils.restoreViewEnabledState(binding.llActivationFormWrapper, enabledStateBeforeActivation, true);
                    enabledStateBeforeActivation = null;
                }
                binding.fabConfirm.setText(R.string.ticket_activation_confirm);
                binding.fabConfirm.setIcon(defaultBtnActivateIcon);
                binding.fabConfirm.setForeground(null);
                binding.fabConfirm.setClickable(true);
                if (viewModel.isActivationSuccessful()) {
                    // do not allow clicking again when the activity is finishing
                    binding.fabConfirm.setEnabled(false);
                }
            }
        });

        viewModel.getActivationError().observe(this, error -> {
            if (error != null) {
                viewModel.ackActivationError();
                CommonDialogs.newInfoDialog(this, getString(R.string.ticket_activation_error_title), error.getMessage())
                        .show();
            }
        });

        viewModel.getActivationResult().observe(this, result -> {
            if (result != null) {
                Log.i(TAG, "Activated ticket; etd=" + result.getEtdAsString());
                finish();
                // debug
                startActivity(new Intent(this, TicketDisplayActivity.class)
                        .putExtra(TicketDisplayActivity.EXTRA_TICKET, result));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding.tvFastActivationTimer.removeCallbacks(fastActivationCountdownPillUpdater);
    }

    private final Runnable fastActivationCountdownPillUpdater = new Runnable() {
        @Override
        public void run() {
            Long secondsLeft = viewModel.getPreauthExpirationSecondsLeft().getValue();
            if (secondsLeft != null && secondsLeft >= 0) {
                TextView pill = binding.tvFastActivationTimer;
                boolean wasGone = pill.getVisibility() == View.GONE;
                pill.setVisibility(secondsLeft > 0 ? View.VISIBLE : View.GONE);
                long minutes = secondsLeft / 60;
                long seconds = secondsLeft % 60;
                int color = R.color.delay_ok;
                if (secondsLeft <= 10) {
                    color = R.color.delay_high;
                } else if (secondsLeft <= 30) {
                    color = R.color.delay_mid;
                }
                pill.setBackgroundTintList(ColorStateList.valueOf(getColor(color)));
                if (wasGone) {
                    if (currentFastPillAnim != null) {
                        currentFastPillAnim.cancel();
                    }
                    pill.setText("");
                    currentFastPillAnim = new PillPopoutAnimation(pill);
                    currentFastPillAnim.start();
                    pill.post(this);
                } else {
                    pill.setText(getString(R.string.fast_activation_countdown_time_format, minutes, seconds));
                }
            } else {
                binding.tvFastActivationTimer.setVisibility(View.GONE);
            }
        }
    };

    private void updateDeviceCheckmark(View checkmark) {
        checkmark.setVisibility((viewModel.isDeviceDataLoading() || (viewModel.isDeviceTrustDecided() && !viewModel.isCurrentDeviceTrusted())) ? View.GONE : View.VISIBLE);
    }

    private String formatValidityStartText() {
        var time = viewModel.getActivationTime().getValue();
        var stop = viewModel.getActivationStop().getValue();
        var zones = viewModel.getChosenZones().getValue();
        if (time == null || zones == null || zones.zones().isEmpty()) {
            return null;
        }
        String zonesFormatted = formatZoneActivationText(viewModel.getTariffSystemId(), zones.zones());
        if (time.isNow()) {
            return getString(R.string.ticket_activation_summary_immediately, zonesFormatted);
        } else {
            String timeFormatted = DateTimeUtils.formatDateTimePhrase(this, time.time(), DateTimeUtils.DateDeclension.ACCUSATIVE);
            TripRouteInfo trip = viewModel.getSelectedRouteInfo();
            if (stop != null && trip != null && !time.isManual()) {
                String lineNameMarkup = trip.trip().trip().line().name();
                String stopNameMarkup = stop.name();
                return getString(
                        R.string.ticket_activation_summary_at_stop,
                        TextMarkupConverter.toPlainText(lineNameMarkup, false),
                        TextMarkupConverter.toPlainText(stopNameMarkup, false),
                        timeFormatted,
                        zonesFormatted
                );
            } else {
                return getString(R.string.ticket_activation_summary_at_time, timeFormatted, zonesFormatted);
            }
        }
    }

    private String formatValidityEndText() {
        LocalDateTime endTime = viewModel.resolveValidityEndTime();
        if (endTime == null) {
            return null;
        }
        String endTimeFormatted = DateTimeUtils.formatDateTimePhrase(this, endTime, DateTimeUtils.DateDeclension.ACCUSATIVE);
        var endStop = viewModel.getValidityEndStop().getValue();
        if (endStop == null) {
            return getString(R.string.ticket_activation_end_summary_time, endTimeFormatted);
        } else {
            return getString(R.string.ticket_activation_end_summary_stop, TextMarkupConverter.toPlainText(endStop.name(), false), endTimeFormatted);
        }
    }

    private String formatZoneActivationText(String tariffSystem, List<String> zones) {
        if (zones == null) {
            return "";
        }
        @PluralsRes int zonesPlural = "PID".equals(tariffSystem) ? R.plurals.zones_pid_accusative : R.plurals.zones_accusative;
        return getString(R.string.ticket_activation_for_zones_format, getResources().getQuantityString(zonesPlural, zones.size()), ListFormat.formatList(this, zones));
    }

    private void setupTariffZoneViews(TripStopInfo point) {
        String tariffZones = point.tariffZones();
        LinearLayout container = binding.activationStop.llZonesContainer;
        if (tariffZones == null || tariffZones.isEmpty()) {
            container.setVisibility(View.GONE);
        } else {
            container.setVisibility(View.VISIBLE);
            container.removeAllViews();

            for (var idsZones : tariffZones.split(";")) {
                TextView zonesView = new TextView(this);
                zonesView.setText(idsZones);
                container.addView(zonesView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
    }

    private void updateDeviceChoiceVisibility() {
        binding.rvDeviceList.setVisibility((viewModel.getCurrentAutoActivationDevice() == null && !viewModel.isZonesManuallyChosen()) ? View.VISIBLE : View.GONE);
        binding.cvActivationStopCard.setVisibility(viewModel.getCurrentActivationStop() != null ? View.VISIBLE : View.GONE);
    }

    private void showZoneChoiceTypeDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ticket_activation_zone_choice_title)
                .setSingleChoiceItems(
                        new String[]{
                                getString(R.string.ticket_activation_zone_choice_auto),
                                getString(R.string.ticket_activation_zone_choice_manual)
                        },
                        viewModel.isZonesManuallyChosen() ? 1 : 0,
                        (dialog, which) -> {
                            dialog.dismiss();
                            if (which == 0) {
                                viewModel.setChosenZonesAuto();
                            } else {
                                launchZonePicker();
                            }
                        }
                )
                .show();
    }

    private void showActivationTimeTypeDialog() {
        List<String> options = new ArrayList<>();
        int autoIndex;
        int immediateIndex;
        int manualIndex;
        if (viewModel.hasActivationStop()) {
            autoIndex = options.size();
            options.add(getString(R.string.ticket_activation_time_auto));
        } else {
            autoIndex = -1;
        }
        immediateIndex = options.size();
        options.add(getString(R.string.ticket_activation_time_immediately));
        manualIndex = options.size();
        options.add(getString(R.string.ticket_activation_time_specify));
        int checkedOption = immediateIndex;
        var current = viewModel.getCurrentActivationTime();
        if (current != null) {
            if (current.isNow()) {
                checkedOption = immediateIndex;
            } else {
                checkedOption = (current.isManual() || !viewModel.hasActivationStop()) ? manualIndex : autoIndex;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ticket_activation_activation_time_title)
                .setSingleChoiceItems(
                        options.toArray(new String[0]),
                        checkedOption,
                        (dialog, which) -> {
                            dialog.dismiss();
                            if (which == autoIndex) {
                                viewModel.setActivationTimeAuto();
                            } else if (which == immediateIndex) {
                                viewModel.setActivationTimeNow();
                            } else if (which == manualIndex) {
                                DateTimePickerDialog.Builder builder = new DateTimePickerDialog.Builder(REQUEST_KEY_ACTIVATION_TIME);
                                builder.setNowMinDateTime();
                                if (current != null && current.time() != null) {
                                    builder.setSelected(current.time());
                                } else {
                                    builder.setSelected(LocalDateTime.now());
                                }
                                builder.build().show(getSupportFragmentManager(), "activation_time_picker");
                            }
                        }
                )
                .show();
    }

    private void launchZonePicker() {
        zonePickerLauncher.launch(new LitackaZonePickerActivity.Input(viewModel.getZoneOptions(), viewModel.getLastChosenZones(), viewModel.getMaxZones()));
    }

    private void launchStopPicker() {
        TripRouteInfo route = viewModel.getSelectedRouteInfo();
        if (route != null) {
            stopPickerLauncher.launch(TripStopPickerActivity.Input.futureRoute(route));
        }
    }

    private void showTicketActivationConfirmation() {
        String mainText = formatValidityStartText();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ticket_activation_confirm_title)
                .setMessage(getString(R.string.ticket_activation_confirm_message, mainText))
                .setPositiveButton(R.string.ticket_activation_confirm_yes, (dialog, which) -> performActivateTicket())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performActivateTicket() {
        viewModel.startActivateViaLwt();
    }

    private static class PillPopoutAnimation {

        private final View pill;

        private ViewPropertyAnimator scale;
        private ValueAnimator corners;

        private final int defaultWidth;
        private final int defaultHeight;

        public PillPopoutAnimation(View pill) {
            this.pill = pill;
            ViewUtils.measureViewForWrapContent(pill);
            defaultWidth = pill.getMeasuredWidth();
            defaultHeight = pill.getMeasuredHeight();
        }

        public void start() {
            PathInterpolator interpolator = new PathInterpolator(0.2f, 0f, 0f, 1f);

            pill.setAlpha(0f);
            pill.setScaleX(0.4f);
            pill.setScaleY(0.4f);
            pill.setPivotX(defaultWidth);
            pill.setPivotY(0);

            scale = pill.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(500)
                    .setInterpolator(interpolator);

            GradientDrawable background = (GradientDrawable) ((RippleDrawable) pill.getBackground()).getDrawable(0);

            corners = ValueAnimator.ofFloat(defaultHeight / 2f, pill.getContext().getResources().getDimensionPixelSize(R.dimen.section_margin_normal));

            corners.setDuration(scale.getDuration());
            corners.setInterpolator(interpolator);

            corners.addUpdateListener(animation -> {
                background.setCornerRadius(
                        (float) animation.getAnimatedValue()
                );
            });

            scale.start();
            corners.start();
        }

        public void cancel() {
            scale.cancel();
            corners.cancel();
        }
    }
}
