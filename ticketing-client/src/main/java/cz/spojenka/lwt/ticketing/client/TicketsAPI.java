package cz.spojenka.lwt.ticketing.client;

import cz.spojenka.lwt.ticketing.api.ProductResponse;
import cz.spojenka.lwt.ticketing.api.TicketActivationParams;
import cz.spojenka.lwt.ticketing.api.TicketResponse;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;

public interface TicketsAPI {

    @GET("/tickets/available")
    public Call<List<ProductResponse>> getAvailableTickets(@Query("includeOutsideValidity") boolean includeOutsideValidity, @Header("If-None-Match") String localETag);

    @POST("/tickets/{id}/activate")
    public Call<TicketResponse> activateTicket(@Query("id") long ticketId, @Body TicketActivationParams params);
}
