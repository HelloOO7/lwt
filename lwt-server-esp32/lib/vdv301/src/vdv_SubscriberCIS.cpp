#include "vdv_SubscriberCIS.h"

#include <iostream>
#include <utility>
#include <regex>
#include "NewAndDelete.h"
#include "FNVHash.h"
#include "vdv_Utility.h"

namespace vdv301
{
    using namespace IBIS_IP_CustomerInformationService_V2_3CZ1_0;

    SubscriberCIS::SubscriberCIS(ServiceDiscovery& sd, Operation subscribedOps) :
        SubscriberHttp(
            sd,
            "CustomerInformationService",
            ServiceDiscovery::QueryBuilder()
            .FilterInstanceName("CustomerInformationService*")
            .FilterTxtRecord("ver", "2.3CZ1.0")
            .Build(),
            std::to_underlying(subscribedOps),
            8192
        )
    {

    }

    void SubscriberCIS::ObserveAllData(Observer<AllData>& observer)
    {
        Observable<AllData>::AddObserver(observer);
    }

    void SubscriberCIS::RemoveObserver(Observer<AllData>& observer) {
        Observable<AllData>::RemoveObserver(observer);
    }

    static std::regex SUBMODE_FIX(R"(<[A-Za-z]+Submode>\s*[A-Za-z]+\s*</[A-Za-z]+Submode>)");

    psram_string FixupXml(const psram_string& input) {
        psram_string output = std::regex_replace(input, SUBMODE_FIX, "");
        return output;
    }

    void SubscriberCIS::OnOperationResult(const OperationResult& result)
    {
        switch (result.GetOperationID<Operation>()) {
        case Operation::GetAllData:
            std::cout << "Received SubscribeAllData result" << std::endl;
            try {
                ssize_t freeBefore = heap_caps_get_free_size(MALLOC_CAP_SPIRAM);
                uint32_t resultHash = HashResponseWithoutTimestamp(result.GetResult());
                if (resultHash == m_LastAllDataHash) {
                    std::cout << "Received data is identical to last received data, ignoring" << std::endl;
                    return;
                }
                auto fixedResult = FixupXml(result.GetResult());
                {
                    UseHeapCaps<MALLOC_CAP_SPIRAM> usePsram;
                    load_data(fixedResult.c_str(), m_LastAllData);
                    m_LastAllDataHash = resultHash;
                }
                ssize_t freeAfter = heap_caps_get_free_size(MALLOC_CAP_SPIRAM);
                std::cout << "Memory used for parsed data: " << (freeBefore - freeAfter) << " bytes, free=" << freeAfter << " bytes" << std::endl;

                if (m_LastAllData.AllData) {
                    std::cout << "Timestamp: " << m_LastAllData.AllData->TimeStamp.Value << std::endl;

                    Observable<AllData>::NotifyObservers(&*m_LastAllData.AllData);
                }
                else {
                    Observable<AllData>::InvalidateObservers();
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

    bool SubscriberCIS::IsTripRefPresent(const TripInformationStructure& tripInfo)
    {
        return !tripInfo.TripRef.Value.empty() && tripInfo.TripRef.Value != "noRef";
    }

    const TripInformationStructure* SubscriberCIS::GetTripInformationFromAllData(const AllData& allData)
    {
        if (allData.TripInformation.empty()) {
            return nullptr;
        }
        return &allData.TripInformation.front();
    }

    const StopInformationStructure* SubscriberCIS::GetCurrentStopFromAllData(const AllData& allData)
    {
        if (allData.CurrentStopIndex.ErrorCode) {
            return nullptr;
        }
        const TripInformationStructure* tripInfo = GetTripInformationFromAllData(allData);
        if (!tripInfo) {
            return nullptr;
        }
        auto index = ConvertStopIndex(allData.CurrentStopIndex);
        if (index < 0 || index >= tripInfo->StopSequence.StopPoint.size()) {
            return nullptr;
        }
        return &tripInfo->StopSequence.StopPoint[index];
    }

    const StopInformationStructure* SubscriberCIS::FindStopByRef(const std::string& stopRef, const AllData& allData)
    {
        for (auto&& tripInfo : allData.TripInformation) {
            for (auto&& stop : tripInfo.StopSequence.StopPoint) {
                if (stop.StopRef.Value == stopRef) {
                    return &stop;
                }
            }
        }
        return nullptr;
    }

    const StopInformationStructure* SubscriberCIS::FindLastStopByRef(const std::string& stopRef, const AllData& allData)
    {
        for (auto tripIt = allData.TripInformation.rbegin(); tripIt != allData.TripInformation.rend(); ++tripIt) {
            for (auto stopIt = tripIt->StopSequence.StopPoint.rbegin(); stopIt != tripIt->StopSequence.StopPoint.rend(); ++stopIt) {
                if (stopIt->StopRef.Value == stopRef) {
                    return &*stopIt;
                }
            }
        }
        return nullptr;
    }

    const DisplayContentStructure* SubscriberCIS::FindDisplayContent(const std::string& displayContentRef, const StopInformationStructure& parent)
    {
        for (auto&& displayContent : parent.DisplayContent) {
            if (displayContent.DisplayContentRef && displayContent.DisplayContentRef->Value == displayContentRef) {
                return &displayContent;
            }
        }
        return nullptr;
    }

    ssize_t SubscriberCIS::ConvertStopIndex(IBIS_IP_int stopIndex)
    {
        if (stopIndex.Value <= 0) {
            return -1;
        }
        return static_cast<ssize_t>(stopIndex.Value - 1); // Convert from 1-based to 0-based index
    }
}