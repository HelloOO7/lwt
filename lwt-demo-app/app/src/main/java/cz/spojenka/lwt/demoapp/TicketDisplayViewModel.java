package cz.spojenka.lwt.demoapp;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import cz.dpp.praguepublictransport.etd.ETDUtils;
import cz.dpp.praguepublictransport.etd.LitackaETD;
import cz.spojenka.android.system.livedata.LiveErrorSignal;
import cz.spojenka.android.ui.drawable.QrCodeDrawable;
import cz.spojenka.lwt.util.PIDTicketTOTP;
import cz.spojenka.lwt.util.TicketTOTP;

public class TicketDisplayViewModel extends AndroidViewModel {

    private static final String TAG = TicketDisplayViewModel.class.getSimpleName();

    private final MutableLiveData<TicketData> ticketLiveData = new MutableLiveData<>();
    private final MutableLiveData<Drawable> qrDrawable = new MutableLiveData<>();
    private final LiveErrorSignal qrDrawableError = new LiveErrorSignal();

    private Drawable lastQRDrawable;
    private String lastQRData;

    private TicketData ticket;
    private TicketTOTP totp;

    public TicketDisplayViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<TicketData> getTicketLiveData() {
        return ticketLiveData;
    }

    public LiveData<Drawable> getQrDrawable() {
        return qrDrawable;
    }

    private String generateQRData() {
        LitackaETD etd = LitackaETD.parse(ticket.getEtdAsString());
        String totpPass = totp.generatePasswordString(Instant.now());
        etd.setProperty("X-TOTP", totpPass);
        return etd.encode();
    }

    private Drawable createQRDrawable() {
        String qrData = generateQRData();
        if (qrData.equals(lastQRData)) {
            return lastQRDrawable;
        }
        lastQRData = qrData;
        lastQRDrawable = new QrCodeDrawable(
                getApplication().getResources(),
                new QrCodeDrawable.Options()
                        .setBackgroundColor(Color.TRANSPARENT)
                        .setPadding(0.05f)
                        .setData(lastQRData)
        );
        return lastQRDrawable;
    }

    public void loadTicket(TicketData ticket) {
        this.ticket = ticket;
        onTicketLoaded();
    }

    public boolean hasTicket() {
        return ticket != null;
    }

    private void onTicketLoaded() {
        ticketLiveData.setValue(ticket);
        totp = new PIDTicketTOTP(ticket.getTotpSeed());
        updateQR();
    }

    public LiveData<Throwable> getQrDrawableError() {
        return qrDrawableError;
    }

    public void updateQR() {
        if (hasTicket()) {
            qrDrawableError.catchError(CompletableFuture.supplyAsync(this::createQRDrawable), qrDrawable::setValue, getApplication().getMainExecutor());
        }
    }
}
