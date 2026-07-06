package cz.spojenka.android.ui.view;

import android.app.Application;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import cz.spojenka.lwt.LocationState;
import cz.spojenka.lwt.TripRouteInfo;
import cz.spojenka.lwt.TripStopInfo;
import cz.spojenka.lwt.demoapp.R;
import cz.spojenka.lwt.util.TextMarkupConverter;

public class ConnectionRouteViewModel extends AndroidViewModel {

    private final MutableLiveData<Boolean> isTimesIncludingDelay = new MutableLiveData<>(true);
    private final MutableLiveData<TripRouteInfo> staticLiveData = new MutableLiveData<>();
    private TripRouteInfo staticData;

    private final MutableLiveData<List<ConnectionRoutePointViewModel>> pointViewModelsLiveData = new MutableLiveData<>();
    private List<ConnectionRoutePointViewModel> pointViewModels = new ArrayList<>();
    private ConnectionRoutePointViewModel currentPoint;

    private int markedRegionStart = -1;
    private int markedRegionEnd = -1;

    private final Observer<TripRouteInfo> staticRouteObserver = route -> {
        this.staticData = route;
        this.staticLiveData.setValue(route);
        List<ConnectionRoutePointViewModel> pointList = new ArrayList<>();
        for (int i = 0; i < route.stopsLength(); ++i) {
            TripStopInfo point = route.stops(i);
            ConnectionRoutePointViewModel pvm = new ConnectionRoutePointViewModel(this, point);
            if (i == 0) {
                pvm.setOrderOnRoute(ConnectionRoutePointViewModel.OrderOnRoute.FIRST);
            } else if (i == route.stopsLength() - 1) {
                pvm.setOrderOnRoute(ConnectionRoutePointViewModel.OrderOnRoute.LAST);
            }
            pointList.add(pvm);
        }
        pointViewModels = pointList;
        updateConnectionPosition();
        markStandardPath();
        markStartAndFinish();
        pointViewModelsLiveData.setValue(pointList);
    };

    private final TextMarkupConverter markupConverter;

    public ConnectionRouteViewModel(Application app) {
        super(app);
        markupConverter = new TextMarkupConverter(app.getResources().getFont(R.font.ropid_piktogramy));
    }

    public TextMarkupConverter getMarkupConverter() {
        return markupConverter;
    }

    public void load(TripRouteInfo tripRoute) {
        staticRouteObserver.onChanged(tripRoute);
    }

    public LiveData<List<ConnectionRoutePointViewModel>> getPointViewModelsLiveData() {
        return pointViewModelsLiveData;
    }

    public List<ConnectionRoutePointViewModel> getPointViewModels() {
        return pointViewModels;
    }

    public ConnectionRoutePointViewModel getCurrentPoint() {
        return currentPoint;
    }

    /**
     * LiveData for whether arrival/departure times at stations should include delay.
     *
     * @return
     */
    public LiveData<Boolean> isTimesIncludingDelay() {
        return isTimesIncludingDelay;
    }

    /**
     * Set whether arrival/departure times at stations should include delay.
     *
     * @param isTimesIncludingDelay
     */
    public void setIsTimesIncludingDelay(boolean isTimesIncludingDelay) {
        this.isTimesIncludingDelay.setValue(isTimesIncludingDelay);
    }

    /**
     * Set the start of the marked region.
     *
     * @param markedRegionStart Index of the first station in the marked region, or -1 if no region is marked.
     */
    public void setMarkedRegionStart(int markedRegionStart) {
        this.markedRegionStart = markedRegionStart;
    }

    /**
     * Set the end of the marked region.
     *
     * @param markedRegionEnd Index of the last station in the marked region, or -1 if no region is marked.
     */
    public void setMarkedRegionEnd(int markedRegionEnd) {
        this.markedRegionEnd = markedRegionEnd;
    }

    /**
     * Get the start of the marked region.
     *
     * @return
     */
    public ConnectionRoutePointViewModel getMarkedRegionStart() {
        if (markedRegionStart == -1) {
            return null;
        }
        return pointViewModels.get(markedRegionStart);
    }

    /**
     * Get the end of the marked region.
     *
     * @return
     */
    public ConnectionRoutePointViewModel getMarkedRegionEnd() {
        if (markedRegionEnd == -1) {
            return null;
        }
        return pointViewModels.get(markedRegionEnd);
    }

    public LiveData<TripRouteInfo> getConnectionLiveData() {
        return staticLiveData;
    }

    public TripRouteInfo getConnectionData() {
        return staticData;
    }

    private void updateRouteSegmentation() {
        markStandardPath();
        markStartAndFinish();
    }

    private void markStartAndFinish() {
        markStart(markedRegionStart == -1 ? 0 : markedRegionStart);
        markFinish(markedRegionEnd == -1 ? pointViewModels.size() - 1 : markedRegionEnd);
    }

    private void markStandardPath() {
        setAllNodeType(ConnectionRouteNode.NodeType.NORMAL);
        markPathSegment(ConnectionRouteNode.PathType.NORMAL, 0, pointViewModels.size() - 1);
        int selectionStart = markedRegionStart;
        if (selectionStart == -1) {
            selectionStart = pointViewModels.indexOf(currentPoint);
        }
        markPathSegment(ConnectionRouteNode.PathType.SELECTED, selectionStart, markedRegionEnd);
    }

    private void markStart(int index) {
        if (index != -1) {
            pointViewModels.get(index).setViewNodeType(ConnectionRouteNode.NodeType.START);
        }
    }

    private void markFinish(int index) {
        if (index != -1) {
            pointViewModels.get(index).setViewNodeType(ConnectionRouteNode.NodeType.FINISH);
        }
    }

    private void setAllNodeType(ConnectionRouteNode.NodeType nodeType) {
        for (var pv : pointViewModels) {
            pv.setViewNodeType(nodeType);
        }
    }

    private void markPathSegment(ConnectionRouteNode.PathType pathType, int from, int to) {
        if (from == -1) {
            from = 0;
        }
        if (to == -1) {
            to = pointViewModels.size() - 1;
        }
        to = Math.min(to, pointViewModels.size() - 1);
        for (int i = Math.max(0, from); i <= to; i++) {
            var n = pointViewModels.get(i);
            if (i == from) {
                n.setViewNodeType(ConnectionRouteNode.NodeType.SEGMENT_START);
            } else {
                n.setViewPathTypeIn(pathType);
            }
            if (i == to) {
                n.setViewNodeType(ConnectionRouteNode.NodeType.SEGMENT_END);
            } else {
                n.setViewPathTypeOut(pathType);
            }
        }
    }

    private void updateConnectionPosition() {
        currentPoint = pointViewModels.get(staticData.trip().currentDepartureStop().sequenceId());
        boolean incoming = staticData.trip().locationState() != LocationState.AtStop;

        for (var pv : pointViewModels) {
            if (pv == currentPoint) {
                pv.setMarker(incoming ? ConnectionRoutePointViewModel.Marker.NEXT_STOP : ConnectionRoutePointViewModel.Marker.AT_STOP);
            } else {
                pv.setMarker(ConnectionRoutePointViewModel.Marker.NONE);
            }
        }
    }

    private int getNextStationViewIndex() {
        return pointViewModels.indexOf(currentPoint);
    }

    /**
     * Update which stations should show delay information based on whether they
     * were passed according to the real-time data.
     */
    private void updateShowDelayMark() {
        int showDelaySince = getNextStationViewIndex();

        for (int i = 0; i < pointViewModels.size(); ++i) {
            pointViewModels.get(i).markShowDelay(i >= showDelaySince);
        }
    }
}
