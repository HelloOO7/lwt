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
import cz.spojenka.android.ui.drawable.QrCodeDrawable;
import cz.spojenka.lwt.util.PIDTicketTOTP;
import cz.spojenka.lwt.util.TicketTOTP;

public class TicketInspectionViewModel extends AndroidViewModel {

    private static final String TAG = TicketInspectionViewModel.class.getSimpleName();

    private final MutableLiveData<TicketData> ticketLiveData = new MutableLiveData<>();
    private final MutableLiveData<Drawable> qrDrawable = new MutableLiveData<>();
    private final MutableLiveData<Throwable> qrDrawableError = new MutableLiveData<>();

    private Drawable lastQRDrawable;
    private String lastQRData;

    private TicketData ticket;
    private TicketTOTP totp;

    public TicketInspectionViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<TicketData> getTicketLiveData() {
        return ticketLiveData;
    }

    public LiveData<Drawable> getQrDrawable() {
        return qrDrawable;
    }

    private String generateQRData() {
        String etd = ticket.getEtdAsString();
        String totpPass = totp.generatePasswordString(Instant.now());
        etd += "X-TOTP:" + totpPass + "*";
        return etd;
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

    private void onTicketLoaded() {
        ticketLiveData.setValue(ticket);
        totp = new PIDTicketTOTP(ticket.getTotpSeed());
        updateQR();
    }

    public LiveData<Throwable> getQrDrawableError() {
        return qrDrawableError;
    }

    public void updateQR() {
        CompletableFuture
                .supplyAsync(this::createQRDrawable)
                .whenCompleteAsync((drawable, throwable) -> {
                    if (drawable != null) {
                        qrDrawable.setValue(drawable);
                    } else {
                        Log.d(TAG, "Error generating QR drawable", throwable);
                        qrDrawableError.setValue(throwable);
                    }
                }, getApplication().getMainExecutor());
    }
}
