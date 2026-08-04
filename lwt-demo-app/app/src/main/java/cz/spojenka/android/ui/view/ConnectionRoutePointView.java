package cz.spojenka.android.ui.view;

import android.content.Context;
import android.text.Html;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.lifecycle.LifecycleOwner;
import cz.spojenka.android.system.livedata.ChildLifecycleOwner;
import cz.spojenka.android.ui.helpers.FlexBoxGapDrawable;
import cz.spojenka.android.ui.helpers.ViewRecycler;
import cz.spojenka.android.util.DateTimeUtils;
import cz.spojenka.android.util.LiveDataUtils;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.TripRouteInfo;
import cz.spojenka.lwt.TripStopInfo;
import cz.spojenka.lwt.demoapp.R;
import cz.spojenka.lwt.demoapp.databinding.ConnectionRoutePointBinding;
import cz.spojenka.lwt.util.LwtTime;

/**
 * View for a single station in a connection route.
 * The view shows the route node graphic, the name of the station as well along with
 * the stop characteristics (request stop, platform) and the arrival and departure times.
 */
public class ConnectionRoutePointView {

    private final ConnectionRoutePointBinding binding;

    private ConnectionRoutePointViewModel viewModel;
    private ChildLifecycleOwner lifecycle;

    private final ViewRecycler<TextView> tariffZoneViewRecycler;

    /**
     * Create a new ConnectionRoutePointView.
     *
     * @param context Context
     * @param parent  Parent ViewGroup
     */
    public ConnectionRoutePointView(Context context, ViewGroup parent) {
        binding = ConnectionRoutePointBinding.inflate(LayoutInflater.from(context), parent, false);
        binding.stationInfoContainer.setDividerDrawableVertical(FlexBoxGapDrawable.horizontal(ViewUtils.dpToPx(context, 8))); //sic
        binding.stationInfoContainer.setDividerDrawableHorizontal(FlexBoxGapDrawable.vertical(ViewUtils.dpToPx(context, 4)));
        tariffZoneViewRecycler = new ViewRecycler<>(context) {
            @Override
            protected TextView newView(Context context) {
                TextView zonesView = new TextView(context);
                zonesView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.gravity = zonesView.getGravity();
                zonesView.setLayoutParams(lp);

                return zonesView;
            }
        };
    }

    public void bind(ConnectionRoutePointViewModel viewModel, LifecycleOwner lifecycleOwner) {
        if (this.lifecycle != null) {
            this.lifecycle.reset();
        }
        this.lifecycle = new ChildLifecycleOwner(lifecycleOwner);
        this.viewModel = viewModel;

        viewModel.getPointLiveData().observe(lifecycle, unused -> onSetStaticData());
        viewModel.getMarkerLiveData().observe(lifecycle, unused -> {
            updateStationText();
            updateCurrentNodeDraw();
        });
        viewModel.getOrderOnRouteLiveData().observe(lifecycle, unused -> updateNodePathsVisibility());
        LiveDataUtils.observeAll(
                lifecycle,
                this::updateMutableUI,
                viewModel.shouldDisplayDelayLiveData()
        );
        viewModel.getViewPathTypeInLiveData().observe(lifecycle, pt -> getNodeView().setPathTypeIn(pt));
        viewModel.getViewPathTypeOutLiveData().observe(lifecycle, pt -> getNodeView().setPathTypeOut(pt));
        viewModel.getViewNodeTypeLiveData().observe(lifecycle, pt -> getNodeView().setNodeType(pt));
        viewModel.getIsShowNodeLiveData().observe(lifecycle, pt -> getNodeView().setShowNode(pt));
    }

    private void onSetStaticData() {
        setupTariffZoneViews();
        updateStationText();
        updateMutableUI();
    }

    private void updateNodePathsVisibility() {
        ConnectionRoutePointViewModel.OrderOnRoute order = viewModel.getOrderOnRoute();
        ConnectionRouteNode nodeView = getNodeView();
        nodeView.setShowPathIn(order != ConnectionRoutePointViewModel.OrderOnRoute.FIRST);
        nodeView.setShowPathOut(order != ConnectionRoutePointViewModel.OrderOnRoute.LAST);
    }

    private void updateCurrentNodeDraw() {
        getNodeView().setNodeCurrent(viewModel.getMarker() != ConnectionRoutePointViewModel.Marker.NONE);
    }

    /**
     * Get the {@link ConnectionRouteNode} view that is used to display the route node graphic.
     *
     * @return
     */
    private ConnectionRouteNode getNodeView() {
        return binding.connectionRouteNode;
    }

    /**
     * Get the root View of the ConnectionRoutePointView.
     *
     * @return The root View
     */
    public View getRoot() {
        return binding.getRoot();
    }

    /**
     * Set an OnClickListener for the root of the View.
     *
     * @param listener The listener
     */
    public void setOnClickListener(View.OnClickListener listener) {
        binding.getRoot().setOnClickListener(listener);
    }

    private void updateStationText() {
        TripStopInfo point = viewModel.getPoint();
        ConnectionRoutePointViewModel.Marker cellMarker = viewModel.getMarker();
        boolean isCurrent = cellMarker != ConnectionRoutePointViewModel.Marker.NONE;

        String stationName = point.stopRef().name();
        if (isCurrent) {
            String statusText = binding.getRoot().getContext().getString(getCellMarkerString(cellMarker));
            binding.tvStationName.setTextSize(16);
            stationName = "<b>" + stationName + "</b>";
            CharSequence stationNameFormatted = viewModel.getMarkupConverter().toSpannableString(stationName);
            binding.tvStationName.setText(TextUtils.concat(Html.fromHtml("<i>" + statusText + "</i>", 0), "\n", stationNameFormatted));
            binding.getRoot().setBackground(AppCompatResources.getDrawable(binding.getRoot().getContext(), R.drawable.current_station_bg));
        } else {
            binding.tvStationName.setTextSize(14);
            binding.tvStationName.setText(viewModel.getMarkupConverter().toSpannableString(stationName));
            binding.getRoot().setBackground(null);
        }

        getNodeView().setNodeCurrent(isCurrent);
    }

    private @StringRes int getCellMarkerString(ConnectionRoutePointViewModel.Marker cellMarker) {
        return switch (cellMarker) {
            case NONE -> 0;
            case AT_STOP -> R.string.vehicle_at_stop_title;
            case NEXT_STOP -> R.string.vehicle_next_stop_title;
        };
    }

    private boolean shouldDisplayDelay() {
        return viewModel.shouldDisplayDelay();
    }

    private boolean shouldDisplayActualArrDep() {
        return viewModel.shouldDisplayActualArrDep();
    }

    private LocalTime adjustTime(LocalTime time) {
        if (shouldDisplayDelay()) {
            TripRouteInfo realTimeConnection = viewModel.getTripRouteInfo();
            if (realTimeConnection != null) {
                return time.plusMinutes(realTimeConnection.trip().delay());
            }
        }
        return time;
    }

    private String getTimeString(LocalTime time, boolean skipDelay) {
        if (!skipDelay) {
            time = adjustTime(time);
        }
        return DateTimeUtils.formatTime(time);
    }

    private void setTimeToTextView(LocalTime time, TextView view, boolean skipDelay) {
        if (time == null) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            String newText = getTimeString(time, skipDelay);
            if (!newText.equals(view.getText().toString())) {
                // minioptimization - setText can sometimes be slow
                view.setText(newText);
            }

            if (!skipDelay && shouldDisplayDelay()) {
                view.setTextColor(MaterialColors.getColor(view, android.R.attr.colorError));
            } else {
                view.setTextColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface));
            }
        }
    }

    private void setTimeDisplay(LocalTime time, TextView textView) {
        setTimeToTextView(time, textView, false);
    }

    private void setArrivalTime(LocalTime arrivalTime) {
        setTimeDisplay(arrivalTime, binding.tvArrivalTime);
    }

    private void setDepartureTime(LocalTime departureTime) {
        setTimeDisplay(departureTime, binding.tvDepartureTime);
    }

    /**
     * Update UI after changes in the data or configuration (such as the "include delay" setting).
     */
    void updateMutableUI() {
        TripStopInfo point = viewModel.getPoint();

        LocalTime arrTime = getPointArrTime(point);
        LocalTime depTime = getPointDepTime(point);
        if (arrTime != null || depTime != null) {
            if (!Objects.equals(DateTimeUtils.withSecondsZero(arrTime), DateTimeUtils.withSecondsZero(depTime))) {
                setArrivalTime(arrTime);
            } else {
                setArrivalTime(null);
            }
            setDepartureTime(depTime);
        } else {
            setArrivalTime(null);
            setDepartureTime(null);
        }

        clearDetailViews();
    }

    private void addFeatureView(View view) {
        binding.stationInfoContainer.addView(view);
    }

    private void setupTariffZoneViews() {
        TripStopInfo point = viewModel.getPoint();
        String tariffZones = point.tariffZones();
        if (tariffZones == null || tariffZones.isEmpty()) {
            binding.llZonesContainer.setVisibility(View.GONE);
        } else {
            binding.llZonesContainer.setVisibility(View.VISIBLE);
            binding.llZonesContainer.removeAllViews();

            for (var idsZones : tariffZones.split(";")) {
                TextView zonesView = tariffZoneViewRecycler.getView();
                zonesView.setText(idsZones);
                binding.llZonesContainer.addView(zonesView);
            }
        }
    }

    private void clearDetailViews() {
        int detailViewsCount = binding.stationInfoContainer.getChildCount() - 1;
        if (detailViewsCount > 0) {
            binding.stationInfoContainer.removeViews(1, detailViewsCount);
        }
    }

    public void forceTimeTextWidth(int width) {
        binding.tvArrivalTime.setWidth(width);
        binding.tvDepartureTime.setWidth(width);
        binding.llTimesContainer.setMinimumWidth(width);
    }

    public int measureTimeTextWidth(String timeText) {
        return (int) Math.ceil(ViewUtils.getTextWidth(binding.tvDepartureTime, timeText));
    }

    public int measureMinDividerOffset(ConnectionRoutePointViewModel viewModel) {
        TripStopInfo point = viewModel.getPoint();
        setTimeToTextView(getPointArrTime(point), binding.tvArrivalTime, true);
        setTimeToTextView(getPointDepTime(point), binding.tvDepartureTime, true);
        ViewUtils.measureViewForWrapContent(binding.llTimesContainer);
        return binding.getRoot().getPaddingStart()
                + binding.connectionRouteNode.getResources().getDimensionPixelSize(R.dimen.connection_route_point_node_width)
                + binding.llTimesContainer.getMeasuredWidth()
                + binding.stationInfoContainer.getPaddingStart();
    }

    private LocalTime getPointArrTime(TripStopInfo point) {
        LocalDateTime ts = LwtTime.convertLocalDateTime(point.arrTime());
        return ts != null ? ts.toLocalTime() : null;
    }

    private LocalTime getPointDepTime(TripStopInfo point) {
        LocalDateTime ts = LwtTime.convertLocalDateTime(point.depTime());
        return ts != null ? ts.toLocalTime() : null;
    }
}
