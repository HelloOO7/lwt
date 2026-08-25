#pragma once

#include "vdv_SubscriberHttp.h"
#include "EnumBitflags.h"
#include "Observable.h"

#include "IBIS_IP_TicketValidationService_V2_3CZ1_0.hpp"

namespace vdv301
{
    namespace TVS = IBIS_IP_TicketValidationService_V2_3CZ1_0;

    class SubscriberTVS : public SubscriberHttp,
        Observable<TVS::TicketValidationService_RazziaResponseDataStructure>,
        Observable<TVS::TicketValidationService_CurrentTariffStopDataStructure>
    {
    public:
        enum class Operation : OperationIDType {
            CurrentTariffStop = DefineOp(0),
            Razzia = DefineOp(1),
            CurrentLine = DefineOp(2),
            VehicleData = DefineOp(3),
            TripData = DefineOp(4)
        };

        using RazziaState = TVS::TicketValidationService_RazziaResponseDataStructure;
        using CurrentTariffStop = TVS::TicketValidationService_CurrentTariffStopDataStructure;

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