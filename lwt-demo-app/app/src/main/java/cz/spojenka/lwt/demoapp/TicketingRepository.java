package cz.spojenka.lwt.demoapp;

import android.app.Application;

import cz.spojenka.lwt.ticketing.client.TicketingAuthenticator;
import cz.spojenka.lwt.ticketing.client.TicketingClient;

public class TicketingRepository {

    private static TicketingRepository INSTANCE;

    private final Application app;

    private TicketingRepository(Application app) {
        this.app = app;
    }

    public static synchronized TicketingRepository getInstance(Application app) {
        if (INSTANCE == null) {
            INSTANCE = new TicketingRepository(app);
        }
        return INSTANCE;
    }

    public TicketingClient createTicketingClient() {
        return createTicketingClient(AccountRepository.getInstance(app).createAuthenticator());
    }

    public TicketingClient createTicketingClient(TicketingAuthenticator authenticator) {
        return new TicketingClient(BuildConfig.TICKETING_SERVER_URL, GlobalTrustManager.createMosNetworkClient(app), authenticator);
    }
}
