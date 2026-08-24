#pragma once

#include "vdv_SubscriberHttp.h"
#include "EnumBitflags.h"
#include "IBIS_IP_CustomerInformationService_V2_3CZ1_0.hpp"
#include "Observable.h"

namespace vdv301
{
    namespace CIS = IBIS_IP_CustomerInformationService_V2_3CZ1_0;

    class SubscriberCIS : public SubscriberHttp, private Observable<CIS::CustomerInformationService_AllData>
    {
    private:
        using AllDataResponse = CIS::CustomerInformationService_GetAllDataResponseStructure;
    public:
        using AllData = CIS::CustomerInformationService_AllData;

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

        static bool IsTripRefPresent(const CIS::TripInformationStructure& tripInfo);
        static const CIS::TripInformationStructure* GetTripInformationFromAllData(const AllData& allData);
        static const CIS::StopInformationStructure* GetCurrentStopFromAllData(const AllData& allData);
        static const CIS::StopInformationStructure* FindStopByRef(const std::string& stopRef, const AllData& allData);
        static const CIS::StopInformationStructure* FindLastStopByRef(const std::string& stopRef, const AllData& allData);
        static const CIS::DisplayContentStructure* FindDisplayContent(
            const std::string& displayContentRef, const CIS::StopInformationStructure& parent
        );

        static ssize_t ConvertStopIndex(CIS::IBIS_IP_int stopIndex);

    protected:
        void OnOperationResult(const OperationResult& result) override;

        std::string GetOperationName(OperationIDType operation) const override;
    };

    DEFINE_ENUM_FLAG_OPERATORS(SubscriberCIS::Operation);
}