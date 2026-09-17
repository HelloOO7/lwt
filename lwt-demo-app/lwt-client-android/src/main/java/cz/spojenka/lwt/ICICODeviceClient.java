package cz.spojenka.lwt;

import java.util.concurrent.CompletableFuture;

public interface ICICODeviceClient extends AutoCloseable {

    public CompletableFuture<CheckInIntermediate> startCheckIn(byte[] checkInToken);
    public CompletableFuture<CICOTicketFragment> confirmCheckIn(CheckInIntermediate intermediate, PresenceTrackingClient presenceTrackingClient);
    public CompletableFuture<CICOTicketFragment> refreshTicket(CICOTicketFragment lastTicket, PresenceTrackingClient presenceTrackingClient);
    public CompletableFuture<CheckOutResponse> checkOut(CICOTicketFragment currentTicket);
    @Override
    public void close();
}
