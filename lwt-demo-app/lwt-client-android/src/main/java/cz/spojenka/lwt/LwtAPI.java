package cz.spojenka.lwt;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public interface LwtAPI {

    @LwtOperation(Operation.Ping)
    public CompletableFuture<PingResponse> ping();

    @LwtOperation(Operation.GetTicketValidationInfo)
    public CompletableFuture<TicketValidationInfo> getTicketValidationInfo(ByteBuffer request);
}
