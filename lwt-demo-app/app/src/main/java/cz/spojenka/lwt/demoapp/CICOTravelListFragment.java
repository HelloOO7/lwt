package cz.spojenka.lwt.demoapp;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;
import cz.spojenka.android.system.SubscreenSwitcher;
import cz.spojenka.android.system.livedata.LiveList;
import cz.spojenka.android.ui.fragment.BaseViewBindingFragment;
import cz.spojenka.android.ui.helpers.AdapterListObserver;
import cz.spojenka.android.ui.helpers.AutoLoadMoreAdapter;
import cz.spojenka.android.ui.helpers.BasicListAdapter;
import cz.spojenka.android.ui.helpers.VerticalSpaceItemDecoration;
import cz.spojenka.android.ui.resources.CurrencyFormat;
import cz.spojenka.android.util.DateTimeUtils;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.databinding.CicoSessionItemBinding;
import cz.spojenka.lwt.demoapp.databinding.FragmentCicoTravelListBinding;
import cz.spojenka.lwt.ticketing.api.CICOClearedFare;
import cz.spojenka.lwt.ticketing.api.CICOSessionInfo;
import cz.spojenka.lwt.ticketing.api.FareSection;

public class CICOTravelListFragment extends BaseViewBindingFragment<FragmentCicoTravelListBinding> {

    private static final String ARG_TYPE = "type";

    public static final int TYPE_ACTIVE = 0;
    public static final int TYPE_HISTORY = 1;

    private CICOFareHistoryViewModel viewModel;

    private AdapterBase<?, ?> adapter;
    private SubscreenSwitcher noResultsSwitcher;

    public static CICOTravelListFragment newInstance(int type) {
        Bundle args = new Bundle();
        args.putInt(ARG_TYPE, type);
        CICOTravelListFragment fragment = new CICOTravelListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected void onSetupView(@Nullable Bundle savedInstanceState) {
        super.onSetupView(savedInstanceState);
        noResultsSwitcher = new SubscreenSwitcher(binding.rvSessionList, binding.noResults);
        binding.rvSessionList.addItemDecoration(new VerticalSpaceItemDecoration(ViewUtils.dpToPx(requireContext(), 4)));
        int type = getArguments() != null ? requireArguments().getInt(ARG_TYPE) : TYPE_ACTIVE;
        createViewModelInActivity(CICOFareHistoryViewModel.class, vm -> {
            this.viewModel = vm;
            if (type == TYPE_ACTIVE) {
                setupActiveList(viewModel.getActiveSessions());
                setupAutoload(viewModel.getHasMoreActiveSessions());
            } else {
                setupHistoryList(viewModel.getFareHistory());
                setupAutoload(viewModel.getHasMoreFareHistory());
            }
        });
    }

    private void setupActiveList(LiveList<CICOSessionInfo> list) {
        ActiveSessionsAdapter adapter = new ActiveSessionsAdapter();
        attachListToAdapter(list, adapter);
        setAdapter(adapter);
        setNoResultsText(R.string.cico_history_no_active_sessions);
    }

    private void setupHistoryList(LiveList<CICOClearedFare> list) {
        HistoryFareAdapter adapter = new HistoryFareAdapter();
        attachListToAdapter(list, adapter);
        setAdapter(adapter);
        setNoResultsText(R.string.cico_history_no_fare_history);
    }

    private void setNoResultsText(@StringRes int text) {
        binding.noResults.setText(text);
    }

    private <T> void attachListToAdapter(LiveList<T> list, AdapterBase<T, ?> adapter) {
        new AdapterListObserver<>(adapter){
            @Override
            public void onChanged(List<T> list) {
                super.onChanged(list);
                updateNoResultsState(list);
            }

            @Override
            public void onInserted(int start, List<T> items) {
                super.onInserted(start, items);
                updateNoResultsState(list.asList());
            }
        }.attach(list);
    }

    private void updateNoResultsState(List<?> listVal) {
        if (listVal.isEmpty()) {
            noResultsSwitcher.setSubscreen(binding.noResults);
        } else {
            noResultsSwitcher.setSubscreen(binding.rvSessionList);
        }
    }

    private void setAdapter(AdapterBase<?, ?> adapter) {
        this.adapter = adapter;
        binding.rvSessionList.setAdapter(adapter);
    }

    private void setupAutoload(LiveData<Boolean> isEnabledLiveData) {
        isEnabledLiveData.observe(getViewLifecycleOwner(), isEnabled -> {
            if (adapter != null) {
                adapter.setLoadNextButtonEnabled(isEnabled);
            }
        });
    }

    @Nullable
    @Override
    protected Class<FragmentCicoTravelListBinding> getViewBindingClass() {
        return FragmentCicoTravelListBinding.class;
    }

    private static abstract class AdapterBase<T, V extends BasicListAdapter.BasicViewHolder<T>> extends AutoLoadMoreAdapter<T, V> {

        @Override
        protected boolean isDefaultShowLoadPrev() {
            return false;
        }

        @Override
        protected void onLoadPrevRequested() {

        }

        protected CicoSessionItemBinding createView(ViewGroup parent) {
            return CicoSessionItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        }
    }

    private class ActiveSessionsAdapter extends AdapterBase<CICOSessionInfo, ActiveSessionViewHolder> {

        @Override
        protected RecyclerView.ViewHolder onCreateItemViewHolder(@NonNull ViewGroup parent) {
            return new ActiveSessionViewHolder(createView(parent));
        }

        @Override
        protected void onLoadNextRequested() {
            viewModel.loadMoreActiveSessions();
        }
    }

    private abstract class ViewHolderBase<T> extends BasicListAdapter.BasicViewHolder<T> {

        protected final CicoSessionItemBinding binding;

        public ViewHolderBase(CicoSessionItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        protected void setSessionTitleByDate(OffsetDateTime date) {
            binding.tvSessionTitle.setText(DateTimeUtils.formatDate(date.toLocalDate()));
        }

        protected CharSequence createStopText(String stopName, OffsetDateTime time, @StringRes int prefix, @StringRes int resIdWithout) {
            if (stopName == null) {
                return itemView.getContext().getText(resIdWithout);
            } else {
                return TextUtils.concat(
                        itemView.getContext().getText(prefix),
                        cz.spojenka.android.util.TextUtils.createStyledText(stopName, Typeface.BOLD),
                        " (" + DateTimeUtils.formatTime(time.toLocalTime()) + ")"
                );
            }
        }
    }

    private class ActiveSessionViewHolder extends ViewHolderBase<CICOSessionInfo> {


        public ActiveSessionViewHolder(CicoSessionItemBinding binding) {
            super(binding);
        }

        @Override
        public void bind(CICOSessionInfo item) {
            setSessionTitleByDate(item.startedAt());
            binding.tvCheckInLabel.setText(createStopText(item.checkInStop(), item.startedAt(), R.string.cico_history_check_in_label, R.string.cico_history_check_in_label_without));
            binding.tvCheckOutLabel.setText(createStopText(item.checkOutStop(), item.endedAt(), R.string.cico_history_newest_check_out_label, R.string.cico_history_check_out_label_without));

            binding.getRoot().setOnClickListener(v -> viewModel.evaluateSession(item.sessionId()));
        }
    }

    private class HistoryFareAdapter extends AdapterBase<CICOClearedFare, HistoryFareViewHolder> {
        @Override
        protected RecyclerView.ViewHolder onCreateItemViewHolder(@NonNull ViewGroup parent) {
            return new HistoryFareViewHolder(createView(parent));
        }

        @Override
        protected void onLoadNextRequested() {
            viewModel.loadMoreFareHistory();
        }
    }

    private class HistoryFareViewHolder extends ViewHolderBase<CICOClearedFare> {
        public HistoryFareViewHolder(CicoSessionItemBinding binding) {
            super(binding);
        }

        @Override
        public void bind(CICOClearedFare item) {
            Context context = itemView.getContext();

            FareSection.Point start = getFirstFarePoint(item);
            FareSection.Point end = getLastFarePoint(item);

            if (start == null || end == null) {
                return; // unexpected
            }

            setSessionTitleByDate(start.time());
            binding.tvCheckInLabel.setText(createStopText(start.stopName(), start.time(), R.string.cico_history_check_in_label, R.string.cico_history_check_in_label_without));
            binding.tvCheckOutLabel.setText(createStopText(end.stopName(), end.time(), R.string.cico_history_check_out_label, R.string.cico_history_check_out_label_without));
            binding.tvFareTotal.setText(CurrencyFormat.formatCzk(context, item.totalFare()));
            binding.tvFareTotal.setVisibility(View.VISIBLE);

            binding.getRoot().setOnClickListener(v -> startActivity(CICOFareSummaryActivity.newIntent(context, item)));
        }

        private FareSection.Point getFirstFarePoint(CICOClearedFare fare) {
            return farePointStream(fare).min(farePointTimeComparator()).orElse(null);
        }

        private FareSection.Point getLastFarePoint(CICOClearedFare fare) {
            return farePointStream(fare).max(farePointTimeComparator()).orElse(null);
        }

        private Comparator<FareSection.Point> farePointTimeComparator() {
            return Comparator.comparing(FareSection.Point::time);
        }

        private Stream<FareSection.Point> farePointStream(CICOClearedFare fare) {
            return fare.tickets().stream()
                    .flatMap(t -> t.travelRecord().sections().stream())
                    .flatMap(s -> Stream.of(s.from(), s.to()));
        }
    }
}
