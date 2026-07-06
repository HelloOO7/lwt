package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import java.util.Objects;

import androidx.lifecycle.ViewModelProvider;
import cz.spojenka.android.ui.activity.SubActivity;
import cz.spojenka.android.ui.helpers.ArrayListAdapter;
import cz.spojenka.android.ui.helpers.BasicListAdapter;
import cz.spojenka.android.ui.view.ConnectionRouteViewModel;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.TripRouteInfo;
import cz.spojenka.lwt.TripStopInfo;
import cz.spojenka.lwt.demoapp.databinding.ActivityTripStopListBinding;
import cz.spojenka.lwt.util.ByteBufferUtils;
import cz.spojenka.lwt.util.FlatbufferUtils;
import cz.spojenka.lwt.util.TextMarkupConverter;

public class TripStopListActivity extends SubActivity {

    public static final String EXTRA_TRIP_ROUTE_INFO = TripStopListActivity.class.getName() + ".EXTRA_TRIP_ROUTE_INFO";

    private ActivityTripStopListBinding binding;
    private TripInfoViewController tripInfoViewController;
    private TextMarkupConverter textMarkupConverter;

    private TripRouteInfo tripInfo;

    private ConnectionRouteViewModel viewModel;

    public static Intent newIntent(Context context, TripRouteInfo tripRouteInfo) {
        return new Intent(context, TripStopListActivity.class)
                .putExtra(EXTRA_TRIP_ROUTE_INFO, ByteBufferUtils.toByteArray(tripRouteInfo.getByteBuffer()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTripStopListBinding.inflate(getLayoutInflater());
        textMarkupConverter = new TextMarkupConverter(getResources().getFont(R.font.ropid_piktogramy));
        tripInfoViewController = new TripInfoViewController(binding.tripInfoHeader, textMarkupConverter);
        setContentView(binding.getRoot());
        viewModel = new ViewModelProvider(this).get(ConnectionRouteViewModel.class);

        tripInfo = Objects.requireNonNull(FlatbufferUtils.getFlatBufferExtra(getIntent(), EXTRA_TRIP_ROUTE_INFO, TripRouteInfo::getRootAsTripRouteInfo));
        tripInfoViewController.bind(tripInfo.trip());

        viewModel.load(tripInfo);
        configureViewModel(tripInfo, viewModel);

        binding.crvRoute.setRoute(viewModel, this);
        binding.crvRoute.setRoutePointClickListener(this::onPointSelected);
    }

    protected void configureViewModel(TripRouteInfo tripInfo, ConnectionRouteViewModel viewModel) {

    }

    protected TripRouteInfo getTripInfo() {
        return tripInfo;
    }

    protected void onPointSelected(TripStopInfo stop) {

    }
}
