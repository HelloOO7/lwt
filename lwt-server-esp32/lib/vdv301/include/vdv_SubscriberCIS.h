#pragma once

#include "vdv_SubscriberHttp.h"
#include "EnumBitflags.h"
#include "IBIS_IP_CustomerInformationService_V2_3CZ1_0.hpp"

namespace vdv301
{

    class SubscriberCIS : public SubscriberHttp
    {
    public:
        enum class Operation : SubscriberHttp::OperationIDType {
            GetAllData = (1 << 0),
            GetCurrentAnnouncement = (1 << 1),
            GetCurrentConnectionInformation = (1 << 2),
            GetCurrentDisplayContent = (1 << 3),
            GetCurrentStopPoint = (1 << 4),
            GetCurrentStopIndex = (1 << 5),
            GetTripData = (1 << 6),
            GetVehicleData = (1 << 7),
            RetrievePartialStopSequence = (1 << 8),
        };

    private:
        IBIS_IP_CustomerInformationService_V2_3CZ1_0::CustomerInformationService_GetAllDataResponseStructure m_LastAllData;

    public:
        SubscriberCIS(ServiceDiscovery& sd, Operation subscribedOps);

    protected:
        void OnOperationResult(const OperationResult& result) override;

        std::string GetOperationName(OperationIDType operation) const override;
    };

    DEFINE_ENUM_FLAG_OPERATORS(SubscriberCIS::Operation);
}