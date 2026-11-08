package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.util.Objects;

import androidx.annotation.Nullable;
import androidx.core.content.IntentCompat;
import cz.dpp.praguepublictransport.LitackaUtils;
import cz.spojenka.android.ui.activity.BaseActivity;
import cz.spojenka.android.ui.resources.CurrencyFormat;
import cz.spojenka.android.ui.resources.ListFormat;
import cz.spojenka.android.ui.view.TripSegmentView;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.databinding.ActivityCicoFareSummaryBinding;
import cz.spojenka.lwt.demoapp.databinding.JourneyItemTripItemSegmentBinding;
import cz.spojenka.lwt.ticketing.api.CICOClearedFare;
import cz.spojenka.lwt.ticketing.api.CICOTicketResponse;
import cz.spojenka.lwt.ticketing.api.FareSection;
import cz.spojenka.lwt.ticketing.api.TicketResponse;

public class CICOFareSummaryActivity extends BaseActivity {

    public static final String EXTRA_FARE = CICOFareSummaryActivity.class.getName() + ".EXTRA_FARE";

    public static Intent newIntent(Context context, CICOClearedFare fare) {
        return new Intent(context, CICOFareSummaryActivity.class)
                .putExtra(EXTRA_FARE, fare);
    }

    private CICOClearedFare fare;
    private ActivityCicoFareSummaryBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fare = Objects.requireNonNull(IntentCompat.getSerializableExtra(getIntent(), EXTRA_FARE, CICOClearedFare.class));
        binding = ActivityCicoFareSummaryBinding.inflate(getLayoutInflater());
        binding.tvPrice.setText(CurrencyFormat.formatCzk(this, fare.totalFare()));
        FareSection lastSection = getLastFareSection();
        for (CICOTicketResponse ticket : fare.tickets()) {
            for (FareSection ticketSection : ticket.travelRecord().sections()) {
                addTicketSectionRows(ticketSection, ticketSection == lastSection);
            }
            addRowView(createTicketDescTextView(ticket.ticket()));
        }
        setContentView(ViewUtils.wrapInScrollView(binding.getRoot()));
    }

    private FareSection getLastFareSection() {
        if (fare.tickets().isEmpty()) {
            return null;
        }
        CICOTicketResponse lastTicket = fare.tickets().get(fare.tickets().size() - 1);
        return lastTicket.travelRecord().sections().get(lastTicket.travelRecord().sections().size() - 1);
    }

    private void addTicketSectionRows(FareSection section, boolean isFinal) {
        TripSegmentView fromSeg = makeAndAddTripSegmentView(section.from());
        TripSegmentView toSeg = makeAndAddTripSegmentView(section.to());
        fromSeg.setFirst();
        if (section.lineName() != null || section.tripNumber() != null) {
            fromSeg.getDetailsContainer().addView(makeLineInfoTextView(section));
            fromSeg.showDetailsContainer();
        }
        if (!isFinal) {
            toSeg.setLast();
        } else {
            toSeg.setFinish();
        }
    }

    private TripSegmentView makeAndAddTripSegmentView(FareSection.Point point) {
        TripSegmentView seg = makeAndAddTripSegmentView();
        seg.setStationName(point.stopName());
        seg.setTime(point.time().toLocalTime());
        seg.setTariffZone(String.join("/", point.tariffZones()));
        return seg;
    }

    private TripSegmentView makeAndAddTripSegmentView() {
        TripSegmentView seg = new TripSegmentView(JourneyItemTripItemSegmentBinding.inflate(getLayoutInflater(), binding.getRoot(), false));
        addRowView(seg.getRoot());
        return seg;
    }

    private void addRowView(View view) {
        int addIndex = binding.getRoot().getChildCount() - 3;
        binding.getRoot().addView(view, addIndex);
    }

    private TextView createTicketDescTextView(TicketResponse ticket) {
        TextView tv = new TextView(this);
        var zones = LitackaUtils.parseCommaSeparatedList(ticket.validZones());
        int zoneCount = zones.size();
        tv.setText(getString(R.string.cico_fare_summary_ticket_desc, getResources().getQuantityString(R.plurals.zones_locative, zoneCount), ListFormat.formatList(this, zones)));
        ViewUtils.setPaddingDp(tv, 0, 4);
        return tv;
    }

    private TextView makeLineInfoTextView(FareSection section) {
        TextView tv = new TextView(this);
        tv.setTextSize(12);
        tv.setTypeface(tv.getTypeface(), Typeface.ITALIC);
        if (section.tripNumber() != null) {
            if (section.lineName() != null) {
                tv.setText(getString(R.string.cico_fare_summary_line_name_trip_num_format, section.lineName(), section.tripNumber()));
            } else {
                tv.setText(getString(R.string.cico_fare_summary_trip_num_format, section.tripNumber()));
            }
        } else {
            tv.setText(getString(R.string.cico_fare_summary_line_name_format, section.lineName()));
        }
        return tv;
    }
}