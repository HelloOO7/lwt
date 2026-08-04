package cz.spojenka.android.ui.view;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import cz.spojenka.android.util.LiveDataUtils;
import cz.spojenka.lwt.TripRouteInfo;
import cz.spojenka.lwt.TripStopInfo;
import cz.spojenka.lwt.util.TextMarkupConverter;

public class ConnectionRoutePointViewModel {

    private final ConnectionRouteViewModel parent;
    private final MutableLiveData<TripStopInfo> pointLiveData;
    private final MutableLiveData<Boolean> showDelayAtPoint = new MutableLiveData<>(false);
    private final MutableLiveData<Marker> marker = new MutableLiveData<>(Marker.NONE);
    private final MutableLiveData<OrderOnRoute> orderOnRoute = new MutableLiveData<>(OrderOnRoute.NORMAL);

    private final MutableLiveData<ConnectionRouteNode.PathType> pathTypeIn = new MutableLiveData<>(ConnectionRouteNode.PathType.NORMAL);
    private final MutableLiveData<ConnectionRouteNode.PathType> pathTypeOut = new MutableLiveData<>(ConnectionRouteNode.PathType.NORMAL);
    private final MutableLiveData<ConnectionRouteNode.NodeType> nodeType = new MutableLiveData<>(ConnectionRouteNode.NodeType.NORMAL);

    public ConnectionRoutePointViewModel(ConnectionRouteViewModel parent, TripStopInfo point) {
        this.parent = parent;
        this.pointLiveData = new MutableLiveData<>(point);
    }

    public TripRouteInfo getTripRouteInfo() {
        return parent.getConnectionData();
    }

    /**
     * Get the route point object. It is never null.
     *
     * @return The route point object
     */
    public @NonNull TripStopInfo getPoint() {
        return Objects.requireNonNull(pointLiveData.getValue());
    }

    public LiveData<TripStopInfo> getPointLiveData() {
        return pointLiveData;
    }

    public TextMarkupConverter getMarkupConverter() {
        return parent.getMarkupConverter();
    }

    /**
     * Notify the View that it is at a position in the route where it is meaningful to display delays.
     * Realistically, this should be used to only enable adding the delay to stations that the connection
     * has not yet passed.
     *
     * @param showDelay true if the delay should be shown
     */
    public void markShowDelay(boolean showDelay) {
        this.showDelayAtPoint.setValue(showDelay);
    }

    public LiveData<Boolean> shouldDisplayDelayLiveData() {
        LiveData<Boolean> liveShowDelay = showDelayAtPoint;
        LiveData<Boolean> liveTimesIncludingDelay = parent.isTimesIncludingDelay();
        LiveData<TripRouteInfo> liveRealTimeConnection = parent.getConnectionLiveData();

        return LiveDataUtils.combine(() -> {
            var realTimeData = getTripRouteInfo();

            if (realTimeData != null && realTimeData.trip().delay() > 0) { //predstihy nezobrazujeme
                boolean showDelay = liveShowDelay.getValue();
                boolean timesIncludingDelay = liveTimesIncludingDelay.getValue();
                return showDelay && timesIncludingDelay;
            }

            return false;
        }, liveShowDelay, liveTimesIncludingDelay, liveRealTimeConnection);
    }

    public boolean shouldDisplayDelay() {
        return Objects.requireNonNull(shouldDisplayDelayLiveData().getValue());
    }

    public LiveData<Boolean> shouldDisplayActualArrDepLiveData() {
        return showDelayAtPoint;
    }

    public boolean shouldDisplayActualArrDep() {
        return Objects.requireNonNull(shouldDisplayActualArrDepLiveData().getValue());
    }

    public LiveData<Marker> getMarkerLiveData() {
        return marker;
    }

    public Marker getMarker() {
        return marker.getValue();
    }

    public void setMarker(Marker cellMarker) {
        this.marker.setValue(cellMarker);
    }

    public LiveData<OrderOnRoute> getOrderOnRouteLiveData() {
        return orderOnRoute;
    }

    public @NonNull OrderOnRoute getOrderOnRoute() {
        return Objects.requireNonNull(orderOnRoute.getValue());
    }

    public void setOrderOnRoute(OrderOnRoute order) {
        this.orderOnRoute.setValue(order);
    }

    public void setViewPathTypeIn(ConnectionRouteNode.PathType pathType) {
        this.pathTypeIn.setValue(pathType);
    }

    public void setViewPathTypeOut(ConnectionRouteNode.PathType pathType) {
        this.pathTypeOut.setValue(pathType);
    }

    public void setViewNodeType(ConnectionRouteNode.NodeType nodeType) {
        this.nodeType.setValue(nodeType);
    }

    public LiveData<ConnectionRouteNode.PathType> getViewPathTypeInLiveData() {
        return pathTypeIn;
    }

    public LiveData<ConnectionRouteNode.PathType> getViewPathTypeOutLiveData() {
        return pathTypeOut;
    }

    public LiveData<ConnectionRouteNode.NodeType> getViewNodeTypeLiveData() {
        return nodeType;
    }

    public LiveData<Boolean> getIsShowNodeLiveData() {
        return Transformations.map(
                getPointLiveData(),
                point -> point.arrTime() != null || point.depTime() != null
        );
    }

    public static enum Marker {
        NONE,
        AT_STOP,
        NEXT_STOP
    }

    public static enum OrderOnRoute {
        FIRST,
        NORMAL,
        LAST
    }
}
