#include "vdv_SubscriberCIS.h"

#include <iostream>
#include <utility>
#include <regex>
#include "NewAndDelete.h"
#include "FNVHash.h"

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
            std::to_underlying(subscribedOps),
            8192 | EventQueue::STACK_PSRAM_BIT
        )
    {

    }

    void SubscriberCIS::ObserveAllData(SubscriberObserver<AllData>& observer)
    {
        AddObserver<Operation, AllData>(Operation::GetAllData, observer);
    }

    static std::regex VEHICLE_STOP_FIX_0(R"(<VehicleStopRequested>\s*<Value>0</Value>\s*</VehicleStopRequested>)");
    static std::regex VEHICLE_STOP_FIX_1(R"(<VehicleStopRequested>\s*<Value>1</Value>\s*</VehicleStopRequested>)");
    static std::regex SUBMODE_FIX(R"(<[A-Za-z]+Submode>\s*[A-Za-z]+\s*</[A-Za-z]+Submode>)");
    static std::regex TIMESTAMP_RE(R"(<TimeStamp>\s*<Value>([^<]+)</Value>\s*</TimeStamp>)");

    psram_string FixupXml(const psram_string& input) {
        psram_string output = std::regex_replace(input, VEHICLE_STOP_FIX_0, "<VehicleStopRequested><Value>false</Value></VehicleStopRequested>");
        output = std::regex_replace(output, VEHICLE_STOP_FIX_1, "<VehicleStopRequested><Value>true</Value></VehicleStopRequested>");
        output = std::regex_replace(output, SUBMODE_FIX, "");
        return output;
    }

    psram_string RemoveTimestampFromXml(const psram_string& input) {
        return std::regex_replace(input, TIMESTAMP_RE, "<TimeStamp><Value></Value></TimeStamp>");
    }

    void SubscriberCIS::OnOperationResult(const OperationResult& result)
    {
        switch (result.GetOperationID<Operation>()) {
        case Operation::GetAllData:
            std::cout << "Received SubscribeAllData result" << std::endl;
            try {
                ssize_t freeBefore = heap_caps_get_free_size(MALLOC_CAP_SPIRAM);
                uint32_t resultHash = FNV1aHash(RemoveTimestampFromXml(result.GetResult()));
                if (resultHash == m_LastAllDataHash) {
                    std::cout << "Received data is identical to last received data, ignoring" << std::endl;
                    return;
                }
                auto fixedResult = FixupXml(result.GetResult());
                {
                    UseHeapCaps<MALLOC_CAP_SPIRAM> usePsram;
                    IBIS_IP_CustomerInformationService_V2_3CZ1_0::load_data(fixedResult.c_str(), m_LastAllData);
                    m_LastAllDataHash = resultHash;
                }
                ssize_t freeAfter = heap_caps_get_free_size(MALLOC_CAP_SPIRAM);
                std::cout << "Memory used for parsed data: " << (freeBefore - freeAfter) << " bytes, free=" << freeAfter << " bytes" << std::endl;

                if (m_LastAllData.AllData) {
                    std::cout << "Timestamp: " << m_LastAllData.AllData->TimeStamp.Value << std::endl;

                    NotifyObservers(Operation::GetAllData, &*m_LastAllData.AllData);
                } else {
                    NotifyObservers(Operation::GetAllData, (AllData*) nullptr);
                }
            }
            catch (const std::exception& e) {
                std::cerr << "Error loading data: " << e.what() << std::endl;
            }
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