#include "lwt_schemas.h"

namespace lwt {

    void ensure_generated_types_linked() {
        (void)sizeof(RequestPacket);
        (void)sizeof(ResponsePacket);
        (void)sizeof(Operation);
        (void)sizeof(PreauthorizationTokenRequest);
        (void)sizeof(PreauthorizationToken);
        (void)sizeof(PreauthorizationTokenResponse);
        (void)sizeof(TicketActivationRequest);
        (void)sizeof(TicketActivationResponse);
        (void)sizeof(StopReference);
        (void)sizeof(LineInfo);
        (void)sizeof(TicketValidationInfo);
        (void)sizeof(TripStopInfo);
        (void)sizeof(TripRouteInfo);
    }
}