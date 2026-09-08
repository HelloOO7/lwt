package cz.spojenka.lwt.demoapp;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;

import java.nio.charset.StandardCharsets;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import cz.spojenka.android.ui.view.LoadingPlaceholderContainer;
import cz.spojenka.lwt.CICOService;
import cz.spojenka.lwt.CICOTicketFragment;
import cz.spojenka.lwt.ICICOService;
import cz.spojenka.lwt.demoapp.databinding.ActivityTicketNoCicoBinding;
import cz.spojenka.lwt.util.ByteBufferUtils;

public class CICOTicketDisplayActivity extends TicketDisplayActivity {

    private final ServiceConnection cicoServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ICICOService cicoService = (ICICOService) service;
            CICOTicketDisplayActivity.this.service = cicoService;
            bindCICOTicket(cicoService.getCurrentTicketLiveData());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private ICICOService service;

    private ActivityTicketNoCicoBinding noCicoBinding;
    private LoadingPlaceholderContainer loadingScreen;
    private View ticketDisplayContent;
    private View noTicketContent;

    private ActivityResultLauncher<Void> checkOutLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindService(CICOService.bindIntent(this), cicoServiceConnection, 0);
        checkOutLauncher = registerForActivityResult(CheckOutActivity.CHECK_OUT_CONTRACT, checkedOut -> {
            if (checkedOut) {
                finish();
            }
        });
    }

    @Override
    protected View decorateContentView(View baseContentView) {
        ticketDisplayContent = baseContentView;
        noCicoBinding = ActivityTicketNoCicoBinding.inflate(getLayoutInflater());
        noTicketContent = noCicoBinding.getRoot();
        return (loadingScreen = LoadingPlaceholderContainer.wrapScreens(this, baseContentView, noTicketContent)).getRoot();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(cicoServiceConnection);
    }

    private void updateNoCicoScreen() {
        if (service.isSessionActive()) {
            noCicoBinding.btnCheckOut.setVisibility(View.VISIBLE);
            noCicoBinding.btnCheckOut.setOnClickListener(v -> checkOutLauncher.launch(null));
        } else {
            noCicoBinding.tvDesc.setText(R.string.cico_ticket_offline_text);
            noCicoBinding.btnCheckOut.setVisibility(View.GONE);
        }
    }

    private void bindCICOTicket(LiveData<CICOTicketFragment> ticket) {
        ticket.observe(this, cicoTicket -> {
            if (cicoTicket == null) {
                updateNoCicoScreen();
                loadingScreen.showContent(noTicketContent);
            } else {
                viewModel.loadTicket(cicoToLocalTicket(cicoTicket));
                loadingScreen.showContent(ticketDisplayContent);
            }
        });
    }

    private TicketData cicoToLocalTicket(CICOTicketFragment cicoTicket) {
        byte[] etdBytes = ByteBufferUtils.toByteArray(cicoTicket.etdAsByteBuffer());
        TicketETDParser etd = new TicketETDParser(new String(etdBytes, StandardCharsets.UTF_8));
        TicketData ticket = new TicketData(
                etd.getIssuerName(),
                0,
                null,
                null,
                null
        );
        ticket.setEtd(etdBytes);
        ticket.setActivatedAt(etd.getValidSince());
        ticket.setValidSince(etd.getValidSince());
        ticket.setValidUntil(etd.getValidUntil());
        ticket.setTotpSeed(ByteBufferUtils.toByteArray(cicoTicket.totpSeedAsByteBuffer()));
        ticket.setChosenZones(null);
        return ticket;
    }
}
