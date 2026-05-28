#include "vdv_SubscriberCIS.h"

#include <iostream>
#include <utility>

namespace vdv301
{
    SubscriberCIS::SubscriberCIS(ServiceDiscovery& sd, Operation subscribedOps) :
        SubscriberHttp(
            sd,
            "CustomerInformationService",
            ServiceDiscovery::QueryBuilder()
            .FilterInstanceName("CustomerInformationService*")
            .FilterTxtRecord("ver", "2.3CZ1.0")
            .Build(),
            std::to_underlying(subscribedOps)
        )
    {

    }

    void SubscriberCIS::OnOperationResult(const OperationResult& result)
    {
        switch (result.GetOperationID<Operation>()) {
        case Operation::GetAllData:
            std::cout << "Received SubscribeAllData result" << std::endl;
            /*try {
                IBIS_IP_CustomerInformationService_V2_3CZ1_0::load_data(result , m_LastAllData);
                std::cout << "Timestamp: " << m_LastAllData.AllData->TimeStamp.Value << std::endl;
            }
            catch (const std::exception& e) {
                std::cerr << "Error loading data: " << e.what() << std::endl;
            }*/
            break;
        default:
            std::cout << "Unexpected operation: " << GetOperationName(result.GetRawOperationID()) << std::endl;
            break;
        }
    }

    std::string SubscriberCIS::GetOperationName(OperationIDType operation) const
    {
        switch (static_cast<Operation>(operation)) {
        case Operation::GetAllData:
            return "GetAllData";
        case Operation::GetCurrentAnnouncement:
            return "GetCurrentAnnouncement";
        case Operation::GetCurrentConnectionInformation:
            return "GetCurrentConnectionInformation";
        case Operation::GetCurrentDisplayContent:
            return "GetCurrentDisplayContent";
        case Operation::GetCurrentStopPoint:
            return "GetCurrentStopPoint";
        case Operation::GetCurrentStopIndex:
            return "GetCurrentStopIndex";
        case Operation::GetTripData:
            return "GetTripData";
        case Operation::GetVehicleData:
            return "GetVehicleData";
        case Operation::RetrievePartialStopSequence:
            return "RetrievePartialStopSequence";
        default:
            return "";
        }
    }
}