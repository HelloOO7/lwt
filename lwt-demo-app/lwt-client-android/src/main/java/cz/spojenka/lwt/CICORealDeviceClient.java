package cz.spojenka.lwt;

import java.util.concurrent.CompletableFuture;

public class CICORealDeviceClient implements ICICODeviceClient {

    private final LwtAPIClient apiClient;

    public CICORealDeviceClient(LwtAPIClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public CompletableFuture<CheckInIntermediate> startCheckIn(byte[] checkInToken) {
        return apiClient.startCheckIn(checkInToken).executeAsync();
    }

    @Override
    public CompletableFuture<CICOTicketFragment> confirmCheckIn(CheckInIntermediate intermediate, PresenceTrackingClient presenceTrackingClient) {
        return apiClient.confirmCheckIn(intermediate, presenceTrackingClient).executeAsync();
    }

    @Override
    public CompletableFuture<CICOTicketFragment> refreshTicket(CICOTicketFragment lastTicket, PresenceTrackingClient presenceTrackingClient) {
        return apiClient.refreshCICO(lastTicket, presenceTrackingClient).executeAsync();
    }

    @Override
    public CompletableFuture<CheckOutResponse> checkOut(CICOTicketFragment currentTicket) {
        return apiClient.checkOut(currentTicket).executeAsync();
    }

    @Override
    public void close() {
        apiClient.close();
    }
}
