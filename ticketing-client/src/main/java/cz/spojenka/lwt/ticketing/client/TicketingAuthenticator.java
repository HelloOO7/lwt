package cz.spojenka.lwt.ticketing.client;

import java.io.IOException;

public interface TicketingAuthenticator {

    public String getAccessToken() throws IOException;
}
