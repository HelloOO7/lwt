#pragma once

#include "vdv_SubscriberHttp.h"
#include "EnumBitflags.h"

#include "IBIS_IP_TicketValidationService_V2_2.hpp"

namespace vdv301
{
    class SubscriberTVS : public SubscriberHttp
    {
    public:
        enum class Operation : SubscriberHttp::OperationIDType {
            GetCurrentStopPoint = (1 << 0),
            GetRazzia = (1 << 1),
            GetCurrentLine = (1 << 2),
            GetVehicleData = (1 << 3),
            RetrieveTripData = (1 << 4)
        };

    private:
        IBIS_IP_TicketValidationService_V2_2::TicketValidationService_RazziaResponseDataStructure m_LastRazziaResp;
        IBIS_IP_TicketValidationService_V2_2::TicketValidationService_CurrentTariffStopDataStructure m_CurTariffStop;

    public:
        SubscriberTVS(ServiceDiscovery& sd, Operation subscribedOps = (Operation) 0);

    protected:
        std::string GetOperationName(OperationIDType operation) const override;
        void OnOperationResult(const OperationResult& result) override;
    };

    DEFINE_ENUM_FLAG_OPERATORS(SubscriberTVS::Operation);
}