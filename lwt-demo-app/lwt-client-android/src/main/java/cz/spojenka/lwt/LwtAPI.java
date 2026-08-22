package cz.spojenka.lwt;

import java.nio.ByteBuffer;

public interface LwtAPI {

    @LwtOperation(Operation.Ping)
    public LwtCall<PingResponse> ping();

    @LwtOperation(Operation.GetTicketValidationInfo)
    public LwtCall<TicketValidationInfo> getTicketValidationInfo(ByteBuffer request);

    @LwtOperation(Operation.AuthenticateServer)
    public LwtCall<ServerAuthenticationResponse> authenticateServer(ByteBuffer request);

    @LwtOperation(Operation.GetTripRouteInfo)
    public LwtCall<TripRouteInfo> getTripRouteInfo();

    @LwtOperation(Operation.GetTicketValidationInfo)
    public LwtCall<TicketValidationInfo> getTicketValidationInfo();

    @LwtOperation(Operation.CreatePreauthorizationToken)
    public LwtCall<PreauthorizationTokenResponse> createPreauthorizationToken(ByteBuffer request);

    @LwtOperation(Operation.ActivateTicket)
    public LwtCall<TicketActivationResponse> activateTicket(ByteBuffer request);

    @LwtOperation(Operation.SetRazzia)
    public LwtCall<SetRazziaResponse> setRazzia(ByteBuffer request);
}
