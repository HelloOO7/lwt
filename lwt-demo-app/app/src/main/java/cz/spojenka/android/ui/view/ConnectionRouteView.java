package cz.spojenka.android.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.divider.MaterialDivider;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cz.spojenka.android.system.livedata.ChildLifecycleOwner;
import cz.spojenka.android.ui.helpers.BasicListAdapter;
import cz.spojenka.android.ui.helpers.EdgeToEdgeSupport;
import cz.spojenka.android.ui.resources.WidestDigitDetector;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.TripRouteInfo;
import cz.spojenka.lwt.TripStopInfo;
import cz.spojenka.lwt.demoapp.R;
import cz.spojenka.lwt.demoapp.databinding.ConnectionNotesTextviewBinding;

public class ConnectionRouteView extends RecyclerView {

    private static final int VT_POINT = 0;
    private static final int VT_POINT_DIVIDER = 1;
    private static final int VT_SECTION_DIVIDER = 2;
    private static final int VT_TIMETABLE_NOTES = 3;

    private RoutePointClickListener listener;

    private ConnectionRouteViewModel viewModel;
    private ChildLifecycleOwner lifecycleOwner;

    private final List<RouteUIElement<?>> uiElements = new ArrayList<>();

    private final LinearLayoutManager llm;

    private final ConnectionRoutePointView measurementDummy;

    private int measuredMinTimeTextWidth;
    private int measuredDividerOffset;

    private List<ExtraNote> extraNotes = new ArrayList<>();

    public ConnectionRouteView(Context context) {
        this(context, null);
    }

    public ConnectionRouteView(Context context, AttributeSet attrs) {
        super(context, attrs);
        measurementDummy = new ConnectionRoutePointView(context, null);
        EdgeToEdgeSupport.installInsets(this, EdgeToEdgeSupport.SIDE_START); //end will be passed to dividers/route points
        llm = new LinearLayoutManager(context);
        setLayoutManager(llm);
        setAdapter(new Adapter());
    }

    /**
     * Attach a listener that will be called when a route point is clicked.
     *
     * @param listener The listener
     */
    public void setRoutePointClickListener(RoutePointClickListener listener) {
        this.listener = listener;
    }

    public void setRoute(ConnectionRouteViewModel viewModel, LifecycleOwner viewLifecycleOwner) {
        if (this.lifecycleOwner != null) {
            this.lifecycleOwner.reset();
        }
        this.viewModel = viewModel;
        this.lifecycleOwner = new ChildLifecycleOwner(viewLifecycleOwner);

        this.viewModel.getPointViewModelsLiveData().observe(this.lifecycleOwner, this::onRouteLoaded);
    }

    public void addExtraNote(ExtraNote note) {
        if (!extraNotes.contains(note)) {
            extraNotes.add(note);
        }
    }

    public boolean scrollToMarkedRegion() {
        var startPt = viewModel.getMarkedRegionStart();
        if (startPt != null) {
            int viewIndex = findElementIndex(startPt);
            if (viewIndex != -1) {
                llm.scrollToPositionWithOffset(viewIndex, 0);
                return true;
            }
        }
        return false;
    }

    public boolean scrollToCurrentRoutePoint() {
        int index = findElementIndex(viewModel.getCurrentPoint());
        if (index != -1) {
            llm.scrollToPositionWithOffset(
                    index,
                    getHeight() / 2 - getResources().getDimensionPixelSize(R.dimen.connection_route_point_min_height)
            );
            return true;
        }
        return false;
    }

    private int findElementIndex(ConnectionRoutePointViewModel point) {
        for (int i = 0; i < uiElements.size(); i++) {
            RouteUIElement<?> element = uiElements.get(i);
            if (element instanceof PointUIElement pui) {
                if (pui.data == point) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void onRouteLoaded(List<ConnectionRoutePointViewModel> route) {
        uiElements.clear();
        for (int i = 0; i < route.size(); i++) {
            ConnectionRoutePointViewModel point = route.get(i);
            boolean last = i == route.size() - 1;

            uiElements.add(new PointUIElement(point));

            if (!last) {
                uiElements.add(new PointDividerUIElement());
            }
        }
        getAdapter().notifyDataSetChanged();
        String notes = formatTimetableNotes(viewModel.getConnectionData());
        if (!notes.isEmpty()) {
            uiElements.add(new SectionDividerUIElement());
            uiElements.add(new TimetableNotesUIElement(notes));
        }
        updateCustomMeasurement();
        requestApplyInsets();
    }

    private String formatTimetableNotes(TripRouteInfo trip) {
        return "";
    }

    private void updateCustomMeasurement() {
        String digit = WidestDigitDetector.get(getContext());
        int minWidth = measurementDummy.measureTimeTextWidth(digit + digit + ":" + digit + digit);
        int maxOffset = 0;

        for (ConnectionRoutePointViewModel viewModel : viewModel.getPointViewModels()) {
            maxOffset = Math.max(maxOffset, measurementDummy.measureMinDividerOffset(viewModel));
        }

        measuredMinTimeTextWidth = minWidth;
        measuredDividerOffset = maxOffset;

        ViewUtils.forEachViewHolder(this, PointViewHolder.class, vh -> {
            vh.pointView.forceTimeTextWidth(measuredMinTimeTextWidth);
        });
        ViewUtils.forEachViewHolder(this, PointDividerViewHolder.class, vh -> {
            LayoutParams lp = (LayoutParams) vh.itemView.getLayoutParams();
            lp.leftMargin = measuredDividerOffset;
            vh.itemView.setLayoutParams(lp);
        });
    }

    public interface RoutePointClickListener {

        void onRoutePointClicked(TripStopInfo point);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // bugfix?? unless this is called on the WHOLE recyclerview, then insets are not
            // applied to elements created after initial layout
            requestApplyInsets();
            return switch (viewType) {
                case VT_POINT -> new PointViewHolder(parent);
                case VT_TIMETABLE_NOTES -> new TimetableNotesViewHolder(parent);
                case VT_POINT_DIVIDER -> {
                    View divider = LayoutInflater.from(parent.getContext()).inflate(R.layout.connection_route_divider, parent, false);
                    LayoutParams lp = (LayoutParams) divider.getLayoutParams();
                    lp.bottomMargin = -getResources().getDimensionPixelSize(R.dimen.route_divider_height);
                    lp.leftMargin = measuredDividerOffset;
                    divider.setLayoutParams(lp);
                    yield new PointDividerViewHolder(divider);
                }
                case VT_SECTION_DIVIDER -> {
                    View divider = new MaterialDivider(parent.getContext());
                    divider.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                    yield new SectionDividerViewHolder(divider);
                }
                default -> throw new IllegalStateException("Unknown view type: " + viewType);
            };
        }

        @Override
        public int getItemViewType(int position) {
            return uiElements.get(position).getItemViewType();
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RouteUIElement raw = uiElements.get(position);
            raw.bindViewHolder(holder);
        }

        @Override
        public int getItemCount() {
            return uiElements.size();
        }
    }

    private interface RouteUIElement<VH extends ViewHolder> {

        public int getItemViewType();

        public default void bindViewHolder(VH vh) {

        }
    }

    private static class PointUIElement implements RouteUIElement<PointViewHolder> {

        private final ConnectionRoutePointViewModel data;

        public PointUIElement(ConnectionRoutePointViewModel data) {
            this.data = data;
        }

        @Override
        public int getItemViewType() {
            return VT_POINT;
        }

        @Override
        public void bindViewHolder(PointViewHolder pointViewHolder) {
            pointViewHolder.bind(this);
        }
    }

    private static class PointDividerUIElement implements RouteUIElement<BasicListAdapter.BasicViewHolder<?>> {

        @Override
        public int getItemViewType() {
            return VT_POINT_DIVIDER;
        }
    }

    private static class SectionDividerUIElement implements RouteUIElement<BasicListAdapter.BasicViewHolder<?>> {

        @Override
        public int getItemViewType() {
            return VT_SECTION_DIVIDER;
        }
    }

    private static class TimetableNotesUIElement implements RouteUIElement<TimetableNotesViewHolder> {

        private final String notesText;

        public TimetableNotesUIElement(String notesText) {
            this.notesText = notesText;
        }

        @Override
        public int getItemViewType() {
            return VT_TIMETABLE_NOTES;
        }

        @Override
        public void bindViewHolder(TimetableNotesViewHolder vh) {
            vh.bind(this);
        }
    }

    private class PointViewHolder extends BasicListAdapter.BasicViewHolder<PointUIElement> {

        private final ConnectionRoutePointView pointView;

        private PointViewHolder(@NonNull ConnectionRoutePointView itemView) {
            super(itemView.getRoot());
            this.pointView = itemView;
            pointView.forceTimeTextWidth(measuredMinTimeTextWidth);
        }

        public PointViewHolder(ViewGroup parent) {
            this(new ConnectionRoutePointView(parent.getContext(), parent));
        }

        @Override
        public void bind(PointUIElement item) {
            pointView.bind(item.data, lifecycleOwner);
            pointView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRoutePointClicked(item.data.getPoint());
                }
            });
        }
    }

    private static class TimetableNotesViewHolder extends BasicListAdapter.BasicViewHolder<TimetableNotesUIElement> {

        private final TextView textView;

        private TimetableNotesViewHolder(TextView view) {
            super(view);
            this.textView = view;
        }

        public TimetableNotesViewHolder(ViewGroup parent) {
            this(ConnectionNotesTextviewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false).getRoot());
        }

        @Override
        public void bind(TimetableNotesUIElement item) {
            textView.setText(item.notesText);
        }
    }

    private static class PointDividerViewHolder extends ViewHolder {

        public PointDividerViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    private static class SectionDividerViewHolder extends ViewHolder {

        public SectionDividerViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static enum ExtraNote {
        RUN_NUMBER
    }
}
