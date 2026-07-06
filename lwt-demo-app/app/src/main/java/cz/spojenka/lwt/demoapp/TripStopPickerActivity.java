package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cz.spojenka.android.ui.dialog.CommonDialogs;
import cz.spojenka.android.ui.view.ConnectionRouteViewModel;
import cz.spojenka.lwt.TripRouteInfo;
import cz.spojenka.lwt.TripStopInfo;
import cz.spojenka.lwt.util.ByteBufferUtils;

public class TripStopPickerActivity extends TripStopListActivity {

    public static final String EXTRA_MIN_SELECTABLE_STOP_INDEX = TripStopPickerActivity.class.getName() + ".EXTRA_MIN_SELECTABLE_STOP_INDEX";
    public static final String EXTRA_MAX_SELECTABLE_STOP_INDEX = TripStopPickerActivity.class.getName() + ".EXTRA_MAX_SELECTABLE_STOP_INDEX";

    public static final String RESULT_EXTRA_SELECTED_STOP = TripStopPickerActivity.class.getName() + ".EXTRA_SELECTED_STOP";

    public static final ActivityResultContract<Input, Integer> PICK_STOP = new ActivityResultContract<>() {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, Input input) {
            return new Intent(context, TripStopPickerActivity.class)
                    .putExtra(EXTRA_TRIP_ROUTE_INFO, ByteBufferUtils.toByteArray(input.tripInfo().getByteBuffer()))
                    .putExtra(EXTRA_MIN_SELECTABLE_STOP_INDEX, input.minSelectableStopIndex())
                    .putExtra(EXTRA_MAX_SELECTABLE_STOP_INDEX, input.maxSelectableStopIndex());
        }

        @Override
        public Integer parseResult(int resultCode, @Nullable Intent intent) {
            if (resultCode == RESULT_OK && intent != null) {
                return intent.getIntExtra(RESULT_EXTRA_SELECTED_STOP, -1);
            }
            return -1;
        }
    };

    private int minSelectableStopIndex;
    private int maxSelectableStopIndex;

    @Override
    protected void configureViewModel(TripRouteInfo tripInfo, ConnectionRouteViewModel viewModel) {
        minSelectableStopIndex = getIntent().getIntExtra(EXTRA_MIN_SELECTABLE_STOP_INDEX, 0);
        maxSelectableStopIndex = getIntent().getIntExtra(EXTRA_MAX_SELECTABLE_STOP_INDEX, getTripInfo().stopsLength() - 1);

        viewModel.setMarkedRegionStart(minSelectableStopIndex);
        viewModel.setMarkedRegionEnd(maxSelectableStopIndex);
    }

    @Override
    protected void onPointSelected(TripStopInfo stop) {
        int index = stop.stopRef().sequenceId();
        if (index < minSelectableStopIndex || index > maxSelectableStopIndex) {
            CommonDialogs.newInfoDialog(this, R.string.trip_stop_picker_invalid_selection_title, R.string.trip_stop_picker_invalid_selection_message).show();
            return;
        }
        setResult(RESULT_OK, new Intent().putExtra(RESULT_EXTRA_SELECTED_STOP, stop.stopRef().sequenceId()));
        finish();
    }

    public static record Input(TripRouteInfo tripInfo, int minSelectableStopIndex, int maxSelectableStopIndex) {

        public static Input entireRoute(TripRouteInfo tripInfo) {
            return new Input(tripInfo, 0, tripInfo.stopsLength() - 1);
        }

        public static Input futureRoute(TripRouteInfo tripInfo) {
            return new Input(tripInfo, tripInfo.trip().currentDepartureStop().sequenceId(), tripInfo.stopsLength() - 1);
        }
    }
}
