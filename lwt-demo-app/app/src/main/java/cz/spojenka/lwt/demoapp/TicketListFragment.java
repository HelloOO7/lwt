package cz.spojenka.lwt.demoapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import cz.spojenka.android.system.SubscreenSwitcher;
import cz.spojenka.android.ui.fragment.BaseViewBindingFragment;
import cz.spojenka.lwt.demoapp.databinding.FragmentTicketListBinding;

public class TicketListFragment extends BaseViewBindingFragment<FragmentTicketListBinding> {

    private SubscreenSwitcher noResultsSwitcher;

    @Override
    protected void onSetupView(@Nullable Bundle savedInstanceState) {
        super.onSetupView(savedInstanceState);
        noResultsSwitcher = new SubscreenSwitcher(binding.rvTicketList, binding.noResults);
        noResultsSwitcher.setSubscreen(binding.noResults);

        binding.fabBuyTicket.setOnClickListener(v -> startActivity(new Intent(requireContext(), TicketOffersActivity.class)));
    }

    @Nullable
    @Override
    protected Class<FragmentTicketListBinding> getViewBindingClass() {
        return FragmentTicketListBinding.class;
    }
}
