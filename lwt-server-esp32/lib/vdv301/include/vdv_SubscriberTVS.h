#pragma once

#include "vdv_SubscriberHttp.h"
#include "EnumBitflags.h"
#include "Observable.h"

#include "IBIS_IP_TicketValidationService_V2_2.hpp"

namespace vdv301
{
    class SubscriberTVS : public SubscriberHttp,
        Observable<IBIS_IP_TicketValidationService_V2_2::TicketValidationService_RazziaResponseDataStructure>,
        Observable<IBIS_IP_TicketValidationService_V2_2::TicketValidationService_CurrentTariffStopDataStructure>
    {
    public:
        enum class Operation : SubscriberHttp::OperationIDType {
            GetCurrentTariffStop = (1 << 0),
            GetRazzia = (1 << 1),
            GetCurrentLine = (1 << 2),
            GetVehicleData = (1 << 3),
            RetrieveTripData = (1 << 4)
        };

        using RazziaState = IBIS_IP_TicketValidationService_V2_2::TicketValidationService_RazziaResponseDataStructure;
        using CurrentTariffStop = IBIS_IP_TicketValidationService_V2_2::TicketValidationService_CurrentTariffStopDataStructure;

    private:
        RazziaState m_LastRazziaResp;
        uint32_t m_LastRazziaRespHash{ 0 };
        CurrentTariffStop m_CurTariffStop;
        uint32_t m_LastCurTariffStopHash{ 0 };

    public:
        SubscriberTVS(ServiceDiscovery& sd, Operation subscribedOps = (Operation) 0);

        void ObserveRazziaState(Observer<RazziaState>& observer);
        void ObserveCurrentTariffStop(Observer<CurrentTariffStop>& observer);

        void RemoveObserver(Observer<RazziaState>& observer);
        void RemoveObserver(Observer<CurrentTariffStop>& observer);

    protected:
        std::string GetOperationName(OperationIDType operation) const override;
        void OnOperationResult(const OperationResult& result) override;
    };

    DEFINE_ENUM_FLAG_OPERATORS(SubscriberTVS::Operation);
}