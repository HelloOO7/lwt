package cz.spojenka.android.ui.view;

import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import java.time.LocalTime;

import androidx.annotation.ColorInt;
import cz.spojenka.android.ui.helpers.FlexBoxGapDrawable;
import cz.spojenka.android.ui.resources.WidestDigitDetector;
import cz.spojenka.android.util.DateTimeUtils;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.databinding.JourneyItemTripItemSegmentBinding;

/**
 * View for displaying a single segment of a trip.
 * This is basically just a station name + arrival/departure time along with a
 * visual marker provided by a {@link ConnectionRouteNode}.
 */
public class TripSegmentView {

    public final JourneyItemTripItemSegmentBinding binding;

    public TripSegmentView(JourneyItemTripItemSegmentBinding binding) {
        this.binding = binding;
        updateTimeTextWidthDefaults();
        binding.stationInfoContainer.setDividerDrawableHorizontal(FlexBoxGapDrawable.vertical(ViewUtils.dpToPx(binding.getRoot(), 2)));
        binding.stationInfoContainer.setDividerDrawableVertical(FlexBoxGapDrawable.horizontal(ViewUtils.dpToPx(binding.getRoot(), 4)));
    }

    private void updateTimeTextWidthDefaults() {
        String digit = WidestDigitDetector.get(binding.tvStationTime.getContext());
        float width = ViewUtils.getTextWidth(binding.tvStationTime, digit + digit + ":" + digit + digit);
        binding.tvStationTime.setWidth((int) Math.ceil(width));
    }

    /**
     * Get the root view of the segment.
     *
     * @return The root view.
     */
    public View getRoot() {
        return binding.getRoot();
    }

    /**
     * Get the container that may be used to display additional data below the station name,
     * such as anticipated delays. Currently not used.
     *
     * @return The container for additional data.
     */
    public LinearLayout getDetailsContainer() {
        return binding.detailsContainer;
    }

    /**
     * Show the container with additional data below the station name.
     */
    public void showDetailsContainer() {
        binding.detailsContainer.setVisibility(View.VISIBLE);
    }

    /**
     * Hide the container shown with {@link #showDetailsContainer()}.
     */
    public void hideDetailsContainer() {
        binding.detailsContainer.setVisibility(View.GONE);
    }

    /**
     * Set the time of arrival/departure at the station.
     *
     * @param time The time of arrival/departure.
     */
    public void setTime(LocalTime time) {
        if (time != null) {
            binding.tvStationTime.setVisibility(View.VISIBLE);
            binding.tvStationTime.setText(DateTimeUtils.formatTime(time));
        } else {
            binding.tvStationTime.setVisibility(View.GONE);
        }
    }

    /**
     * Set the color of the time of arrival/departure at the station.
     *
     * @param color The color of the time.
     */
    public void setTimeColor(@ColorInt int color) {
        binding.tvStationTime.setTextColor(color);
    }

    /**
     * Set the name of the station.
     *
     * @param stationName The name of the station.
     */
    public void setStationName(String stationName) {
        binding.tvStationName.setText(stationName);
    }

    public void setTariffZone(String tariffZone) {
        if (TextUtils.isEmpty(tariffZone)) {
            binding.tvTariffZone.setVisibility(View.GONE);
        } else {
            binding.tvTariffZone.setVisibility(View.VISIBLE);
            binding.tvTariffZone.setText(tariffZone);
        }
    }

    private void setStartPathType(ConnectionRouteNode.PathType pt) {
        if (pt != null) {
            binding.mainRouteNode.setPathTypeIn(pt);
            binding.mainRouteNode.setShowPathIn(true);
        } else {
            binding.mainRouteNode.setShowPathIn(false);
        }
    }

    private void setEndPathType(ConnectionRouteNode.PathType pt) {
        if (pt != null) {
            binding.extRouteNode.setVisibility(View.VISIBLE);
            binding.extRouteNode.setPathTypeIn(pt);
            binding.extRouteNode.setPathTypeOut(pt);
            binding.mainRouteNode.setPathTypeOut(pt);
            binding.mainRouteNode.setShowPathOut(true);
        } else {
            binding.extRouteNode.setVisibility(View.GONE);
            binding.mainRouteNode.setShowPathOut(false);
        }
    }

    private void setNodeCurrentAnimation(boolean animate) {
        binding.mainRouteNode.setNodeCurrent(animate);
    }

    private void resetSpecialNode() {
        setNodeCurrentAnimation(false);
        binding.mainRouteNode.setSpecialNodeRadiusScale(1f);
    }

    /**
     * Set that the point is the first one in the route. This will hide the path leading to it.
     */
    public void setFirst() {
        resetSpecialNode();
        setStartPathType(null);
        setEndPathType(ConnectionRouteNode.PathType.SELECTED);
        binding.mainRouteNode.setNodeType(ConnectionRouteNode.NodeType.SEGMENT_START);
    }

    public void setIntermediate() {
        setIntermediate(false);
    }

    public void setIntermediate(boolean outMuted) {
        resetSpecialNode();
        binding.mainRouteNode.setNodeType(ConnectionRouteNode.NodeType.SEGMENT_END);
        setStartPathType(ConnectionRouteNode.PathType.SELECTED);
        setEndPathType(outMuted ? ConnectionRouteNode.PathType.NORMAL : ConnectionRouteNode.PathType.SELECTED);
    }

    /**
     * Set that the point is the last one in the route. This will hide the path leading from it.
     */
    public void setLast() {
        resetSpecialNode();
        setStartPathType(ConnectionRouteNode.PathType.SELECTED);
        setEndPathType(null);
        binding.mainRouteNode.setNodeType(ConnectionRouteNode.NodeType.SEGMENT_END);
    }

    /**
     * Set that the point is the end of a journey. This will show a special marker as
     * well as call {@link #setLast()}.
     */
    public void setFinish() {
        setLast();
        binding.mainRouteNode.setNodeType(ConnectionRouteNode.NodeType.FINISH);
        binding.mainRouteNode.setSpecialNodeRadiusScale(1.5f);
    }

    public void setRealtimeStart() {
        setFirst();
        setNodeCurrentAnimation(true);
    }
}
