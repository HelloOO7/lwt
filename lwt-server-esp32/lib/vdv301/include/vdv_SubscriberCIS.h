#pragma once

#include "vdv_SubscriberHttp.h"
#include "EnumBitflags.h"
#include "IBIS_IP_CustomerInformationService_V2_3CZ1_0.hpp"
#include "Observable.h"

namespace vdv301
{

    class SubscriberCIS : public SubscriberHttp, private Observable<IBIS_IP_CustomerInformationService_V2_3CZ1_0::CustomerInformationService_AllData>
    {
    private:
        using AllDataResponse = IBIS_IP_CustomerInformationService_V2_3CZ1_0::CustomerInformationService_GetAllDataResponseStructure;
    public:
        using AllData = IBIS_IP_CustomerInformationService_V2_3CZ1_0::CustomerInformationService_AllData;

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
        AllDataResponse m_LastAllData;
        uint32_t m_LastAllDataHash{ 0 };

    public:
        SubscriberCIS(ServiceDiscovery& sd, Operation subscribedOps = (Operation)0);

        void ObserveAllData(Observer<AllData>& observer);
        void RemoveObserver(Observer<AllData>& observer);

        static bool IsTripRefPresent(const IBIS_IP_CustomerInformationService_V2_3CZ1_0::TripInformationStructure& tripInfo);
        static const IBIS_IP_CustomerInformationService_V2_3CZ1_0::TripInformationStructure* GetTripInformationFromAllData(const AllData& allData);
        static const IBIS_IP_CustomerInformationService_V2_3CZ1_0::StopInformationStructure* GetCurrentStopFromAllData(const AllData& allData);
        static const IBIS_IP_CustomerInformationService_V2_3CZ1_0::StopInformationStructure* FindStopByRef(const std::string& stopRef, const AllData& allData);
        static const IBIS_IP_CustomerInformationService_V2_3CZ1_0::StopInformationStructure* FindLastStopByRef(const std::string& stopRef, const AllData& allData);
        static const IBIS_IP_CustomerInformationService_V2_3CZ1_0::DisplayContentStructure* FindDisplayContent(
            const std::string& displayContentRef, const IBIS_IP_CustomerInformationService_V2_3CZ1_0::StopInformationStructure& parent
        );
        
        static ssize_t ConvertStopIndex(IBIS_IP_CustomerInformationService_V2_3CZ1_0::IBIS_IP_int stopIndex);

    protected:
        void OnOperationResult(const OperationResult& result) override;

        std::string GetOperationName(OperationIDType operation) const override;
    };

    DEFINE_ENUM_FLAG_OPERATORS(SubscriberCIS::Operation);
}