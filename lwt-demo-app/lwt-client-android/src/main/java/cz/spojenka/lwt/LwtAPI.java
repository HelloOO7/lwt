package cz.spojenka.lwt;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface LwtAPI {

    @LwtOperation(Operation.Ping)
    public CompletableFuture<PingResponse> ping();

    @LwtOperation(Operation.GetTicketValidationInfo)
    public CompletableFuture<TicketValidationInfo> getTicketValidationInfo(ByteBuffer request);

    @LwtOperation(Operation.AuthenticateServer)
    public CompletableFuture<ServerAuthenticationResponse> authenticateServer(ByteBuffer request);

    @LwtOperation(Operation.GetTripRouteInfo)
    public CompletableFuture<TripRouteInfo> getTripRouteInfo();

    @LwtOperation(Operation.GetTicketValidationInfo)
    public CompletableFuture<TicketValidationInfo> getTicketValidationInfo();

    @LwtOperation(Operation.CreatePreauthorizationToken)
    public CompletableFuture<PreauthorizationTokenResponse> createPreauthorizationToken(ByteBuffer request);

    @LwtOperation(Operation.ActivateTicket)
    public CompletableFuture<TicketActivationResponse> activateTicket(ByteBuffer request);
}
