package cz.spojenka.lwt.demoapp;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.time.OffsetDateTime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;
import cz.spojenka.android.system.livedata.LiveList;
import cz.spojenka.android.ui.fragment.BaseViewBindingFragment;
import cz.spojenka.android.ui.helpers.AdapterListObserver;
import cz.spojenka.android.ui.helpers.AutoLoadMoreAdapter;
import cz.spojenka.android.ui.helpers.BasicListAdapter;
import cz.spojenka.android.ui.helpers.VerticalSpaceItemDecoration;
import cz.spojenka.android.util.DateTimeUtils;
import cz.spojenka.android.util.ViewUtils;
import cz.spojenka.lwt.demoapp.databinding.CicoSessionItemBinding;
import cz.spojenka.lwt.demoapp.databinding.FragmentCicoTravelListBinding;
import cz.spojenka.lwt.ticketing.api.CICOClearedFare;
import cz.spojenka.lwt.ticketing.api.CICOSessionInfo;

public class CICOTravelListFragment extends BaseViewBindingFragment<FragmentCicoTravelListBinding> {

    private static final String ARG_TYPE = "type";

    public static final int TYPE_ACTIVE = 0;
    public static final int TYPE_HISTORY = 1;

    private CICOFareHistoryViewModel viewModel;

    private AdapterBase<?, ?> adapter;

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
        binding.getRoot().addItemDecoration(new VerticalSpaceItemDecoration(ViewUtils.dpToPx(requireContext(), 4)));
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
        new AdapterListObserver<>(adapter).attach(list);
        setAdapter(adapter);
    }

    private void setupHistoryList(LiveList<CICOClearedFare> list) {
        /*HistoryAdapter adapter = new HistoryAdapter();
        new AdapterListObserver<>(adapter).attach(list);
        setAdapter(adapter);*/
    }

    private void setAdapter(AdapterBase<?, ?> adapter) {
        this.adapter = adapter;
        binding.getRoot().setAdapter(adapter);
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
    }

    private class ActiveSessionsAdapter extends AdapterBase<CICOSessionInfo, ActiveSessionViewHolder> {

        @Override
        protected RecyclerView.ViewHolder onCreateItemViewHolder(@NonNull ViewGroup parent) {
            return new ActiveSessionViewHolder(CicoSessionItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        protected void onLoadNextRequested() {
            viewModel.loadMoreActiveSessions();
        }
    }

    private class ActiveSessionViewHolder extends BasicListAdapter.BasicViewHolder<CICOSessionInfo> {

        private final CicoSessionItemBinding binding;

        public ActiveSessionViewHolder(CicoSessionItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        public void bind(CICOSessionInfo item) {
            binding.tvSessionTitle.setText(DateTimeUtils.formatDate(item.startedAt().toLocalDate()));
            binding.tvCheckInLabel.setText(createStopText(item.checkInStop(), item.startedAt(), R.string.cico_history_check_in_label, R.string.cico_history_check_in_label_without));
            binding.tvCheckOutLabel.setText(createStopText(item.checkOutStop(), item.endedAt(), R.string.cico_history_check_out_label, R.string.cico_history_check_out_label_without));

            binding.getRoot().setOnClickListener(v -> viewModel.evaluateSession(item.sessionId()));
        }

        private CharSequence createStopText(String stopName, OffsetDateTime time, @StringRes int prefix, @StringRes int resIdWithout) {
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
}
