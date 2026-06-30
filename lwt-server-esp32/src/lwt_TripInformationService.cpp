#include "lwt_TripInformationService.h"

#include "operations_generated.h"
#include "trip_information_generated.h"
#include "lwt_ApplicationServer.h"
#include "ISO8601.h"
#include <cmath>

namespace lwt {

    using namespace IBIS_IP_CustomerInformationService_V2_3CZ1_0;

    TripInformationService::TripInformationService(vdv301::SubscriberCIS& cis)
        : m_CIS(cis)
    {
        m_CIS.ObserveAllData(*this);
    }

    TripInformationService::~TripInformationService()
    {
        m_CIS.RemoveObserver(*this);
    }

    auto BuildStopReference(flatbuffers::FlatBufferBuilder& fbb, const StopInformationStructure& stopInfo)
    {
        auto stopName = stopInfo.StopName.empty() ? InternationalTextType_Value_t{} : stopInfo.StopName.front().Value;
        auto stopNameRef = fbb.CreateString(stopName);

        return CreateStopReference(
            fbb,
            stopInfo.StopIndex.Value,
            std::stoi(stopInfo.GlobalStopRef.Value),
            stopNameRef
        );
    }

    int64_t ConvertOptDateTime(xsd::optional<IBIS_IP_dateTime> optDateTime)
    {
        if (!optDateTime) {
            return -1;
        }
        auto dt = LocalDateTime::parse(optDateTime->Value);
        return dt.to_epoch_seconds();
    }

    std::string BuildTariffZonesString(const StopInformationStructure& stopInfo)
    {
        std::string zones;
        for (const auto& zone : stopInfo.FareZone) {
            if (!zones.empty()) {
                zones += ";";
            }
            zones += zone.Value;
        }
        return zones;
    }

    auto BuildTripStopInfo(flatbuffers::FlatBufferBuilder& fbb, const StopInformationStructure& stopInfo)
    {
        auto stopRef = fbb.CreateString(stopInfo.StopRef.Value);

        return CreateTripStopInfo(
            fbb,
            BuildStopReference(fbb, stopInfo),
            ConvertOptDateTime(stopInfo.ArrivalScheduled),
            ConvertOptDateTime(stopInfo.DepartureExpected),
            fbb.CreateString(BuildTariffZonesString(stopInfo)),
            NAN // PID does not send this data, skip for now
        );
    }

    auto BuildHeadsign(flatbuffers::FlatBufferBuilder& fbb, const vdv301::SubscriberCIS::AllData& allData, const DestinationStructure& destination) {
        auto destStop = vdv301::SubscriberCIS::FindLastStopByRef(destination.DestinationRef.Value, allData);
        auto destName = destination.DestinationName.empty() ? InternationalTextType_Value_t{} : destination.DestinationName.front().Value;

        return CreateStopReference(
            fbb,
            destStop ? destStop->StopIndex.Value : 0,
            destStop ? std::stoi(destStop->GlobalStopRef.Value) : 0,
            fbb.CreateString(destName)
        );
    }

    auto BuildLineInfo(flatbuffers::FlatBufferBuilder& fbb, const vdv301::SubscriberCIS::AllData& allData, const DisplayContentStructure& displayContent)
    {
        auto&& line = displayContent.LineInformation;

        auto lineNumber = line.LineNumber ? line.LineNumber->Value : 0;

        auto lineName = line.LineName.empty() ? InternationalTextType_Value_t{} : line.LineName.front().Value;
        auto lineNameRef = fbb.CreateString(lineName);

        return CreateLineInfo(
            fbb,
            lineNumber,
            allData.VehicleMode ? TripInformationService::VehicleModeToLineType(*allData.VehicleMode) : LineType::LineType_GenericBus,
            lineNameRef,
            BuildHeadsign(fbb, allData, displayContent.Destination)
        );
    }

    auto BuildTripInfo(flatbuffers::FlatBufferBuilder& fbb, const vdv301::SubscriberCIS::AllData& allData, const TripInformationStructure& tripInfo, const StopInformationStructure& curStop) {
        auto displayContent = vdv301::SubscriberCIS::FindDisplayContent("Interior", curStop);
        if (!displayContent) {
            // element is 1-n, so one is always present
            displayContent = &curStop.DisplayContent.front();
        }

        return CreateTripInfo(
            fbb,
            vdv301::SubscriberCIS::IsTripRefPresent(tripInfo) ? std::stoi(tripInfo.TripRef.Value) : 0,
            BuildLineInfo(fbb, allData, *displayContent)
        );
    }

    auto BuildTripStateInfo(flatbuffers::FlatBufferBuilder& fbb, const vdv301::SubscriberCIS::AllData& allData, const TripInformationStructure& tripInfo, const StopInformationStructure& curStop) {
        return CreateTripStateInfo(
            fbb,
            BuildTripInfo(fbb, allData, tripInfo, curStop),
            tripInfo.TimetableDelay ? tripInfo.TimetableDelay->Value : 0,
            BuildStopReference(fbb, curStop),
            tripInfo.LocationState && *tripInfo.LocationState == LocationStateEnumeration::AtStop ? LocationState_AtStop : LocationState_BeforeStop
        );
    }

    auto BuildTripRouteInfo(flatbuffers::FlatBufferBuilder& fbb, const vdv301::SubscriberCIS::AllData& allData, const TripInformationStructure& tripInfo, const StopInformationStructure& curStop) {
        std::vector<flatbuffers::Offset<TripStopInfo>> stops;
        for (const auto& stop : tripInfo.StopSequence.StopPoint) {
            stops.push_back(BuildTripStopInfo(fbb, stop));
        }
        auto stopsVector = fbb.CreateVector(stops);

        return CreateTripRouteInfo(
            fbb,
            BuildTripStateInfo(fbb, allData, tripInfo, curStop),
            stopsVector
        );
    }

    void TripInformationService::OnDataChanged(const vdv301::SubscriberCIS::AllData* result)
    {
        std::lock_guard lock(m_DataMutex);

        m_MainFBB.Clear();
        if (!result) {
            m_HasData = false;
            return;
        }

        auto tripInfo = vdv301::SubscriberCIS::GetTripInformationFromAllData(*result);
        auto curStop = vdv301::SubscriberCIS::GetCurrentStopFromAllData(*result);

        if (!tripInfo || !curStop) {
            m_HasData = false;
            return;
        }

        m_HasData = true;
        m_MainFBB.Finish(BuildTripRouteInfo(m_MainFBB, *result, *tripInfo, *curStop));
    }

    void TripInformationService::Register(ServiceRegistry& registry)
    {
        registry.RegisterServiceCallback(Operation_GetTripRouteInfo, [&](const RequestPacket& request) -> OperationResult {
            std::lock_guard lock(m_DataMutex);
            if (!m_HasData) {
                return 404;
            }
            return OperationResult(200, SerializeFlatBuffer(m_MainFBB));
        });
    }

    LineType TripInformationService::VehicleModeToLineType(VehicleModeEnumeration mode)
    {
        switch (mode) {
        case VehicleModeEnumeration::air:
            return LineType::LineType_AirportBus;
        case VehicleModeEnumeration::bus:
        case VehicleModeEnumeration::coach:
            return LineType::LineType_GenericBus;
        case VehicleModeEnumeration::ferry:
            return LineType::LineType_Ferry;
        case VehicleModeEnumeration::metro:
            return LineType::LineType_Metro;
        case VehicleModeEnumeration::underground:
        case VehicleModeEnumeration::rail:
            return LineType::LineType_GenericTrain;
        case VehicleModeEnumeration::tram:
            return LineType::LineType_Tram;
        default:
            return LineType::LineType_GenericBus;
        }
    }
}