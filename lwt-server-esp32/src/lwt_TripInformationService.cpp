#include "lwt_TripInformationService.h"

#include "operations_generated.h"
#include "trip_information_generated.h"
#include "lwt_ApplicationServer.h"
#include "ISO8601.h"
#include <cmath>
#include "StringExtensions.h"

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
            vdv301::SubscriberCIS::ConvertStopIndex(stopInfo.StopIndex),
            std::stoi(stopInfo.GlobalStopRef.Value),
            stopNameRef
        );
    }

    LwtLocalDateTime ConvertDateTime(const IBIS_IP_dateTime& dateTime)
    {
        auto dt = LocalDateTime::parse(dateTime.Value);
        return LwtLocalDateTime(dt.to_epoch_seconds());
    }

    auto BuildTripStopInfo(flatbuffers::FlatBufferBuilder& fbb, const TripInformationStructure& tripInfo, const StopInformationStructure& stopInfo)
    {
        bool isFirstStop = stopInfo.StopIndex.Value == tripInfo.StopSequence.StopPoint.front().StopIndex.Value;
        bool isLastStop = stopInfo.StopIndex.Value == tripInfo.StopSequence.StopPoint.back().StopIndex.Value;

        LwtLocalDateTime arrTime;
        LwtLocalDateTime depTime;

        LwtLocalDateTime* pArrTime = nullptr;
        LwtLocalDateTime* pDepTime = nullptr;

        if (!isFirstStop && stopInfo.ArrivalScheduled) {
            arrTime = ConvertDateTime(*stopInfo.ArrivalScheduled);
            pArrTime = &arrTime;
        }
        if (!isLastStop && stopInfo.DepartureScheduled) {
            depTime = ConvertDateTime(*stopInfo.DepartureScheduled);
            pDepTime = &depTime;
        }

        return CreateTripStopInfo(
            fbb,
            BuildStopReference(fbb, stopInfo),
            pArrTime,
            pDepTime,
            fbb.CreateString(TripInformationService::BuildTariffZonesString(stopInfo)),
            NAN // PID does not send this data, skip for now
        );
    }

    auto BuildHeadsign(flatbuffers::FlatBufferBuilder& fbb, const vdv301::SubscriberCIS::AllData& allData, const DestinationStructure& destination) {
        auto destStop = vdv301::SubscriberCIS::FindLastStopByRef(destination.DestinationRef.Value, allData);
        auto destName = destination.DestinationName.empty() ? InternationalTextType_Value_t{} : destination.DestinationName.front().Value;

        return CreateStopReference(
            fbb,
            destStop ? vdv301::SubscriberCIS::ConvertStopIndex(destStop->StopIndex) : 0,
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
            stops.push_back(BuildTripStopInfo(fbb, tripInfo, stop));
        }
        auto stopsVector = fbb.CreateVector(stops);

        return CreateTripRouteInfo(
            fbb,
            BuildTripStateInfo(fbb, allData, tripInfo, curStop),
            stopsVector
        );
    }

    void TripInformationService::OnChanged(const vdv301::SubscriberCIS::AllData* result)
    {
        std::lock_guard lock(m_DataMutex);

        m_MainFBB.Clear();
        if (!result) {
            m_HasData = false;
            Observable<TripStateInfo>::InvalidateObservers();
            return;
        }

        auto tripInfo = vdv301::SubscriberCIS::GetTripInformationFromAllData(*result);
        auto curStop = vdv301::SubscriberCIS::GetCurrentStopFromAllData(*result);

        if (!tripInfo || !curStop) {
            m_HasData = false;
            Observable<TripStateInfo>::InvalidateObservers();
            return;
        }

        m_HasData = true;
        m_MainFBB.Finish(BuildTripRouteInfo(m_MainFBB, *result, *tripInfo, *curStop));

        Observable<TripRouteInfo>::NotifyObservers(GetTripRouteInfo());
        Observable<TripStateInfo>::NotifyObservers(GetTripStateInfo());
    }

    void TripInformationService::ObserveTripStateInfo(Observer<TripStateInfo>& observer)
    {
        Observable<TripStateInfo>::AddObserver(observer);
    }

    void TripInformationService::ObserveTripRouteInfo(Observer<TripRouteInfo>& observer)
    {
        Observable<TripRouteInfo>::AddObserver(observer);
    }

    void TripInformationService::RemoveObserver(Observer<TripStateInfo>& observer)
    {
        Observable<TripStateInfo>::RemoveObserver(observer);
    }

    void TripInformationService::RemoveObserver(Observer<TripRouteInfo>& observer)
    {
        Observable<TripRouteInfo>::RemoveObserver(observer);
    }

    flatbuffers::Offset<TripStateInfo> TripInformationService::GetTripStateInfo(flatbuffers::FlatBufferBuilder& fbb)
    {
        std::lock_guard lock(m_DataMutex);

        const auto* src = GetTripStateInfo();

        if (!src) {
            return {};
        }

        return CreateTripStateInfo(
            fbb,
            GetTripInfo(fbb, src->trip()),
            src->delay(),
            GetStopReference(fbb, src->current_departure_stop()),
            src->location_state()
        );
    }

    flatbuffers::Offset<TripInfo> TripInformationService::GetTripInfo(flatbuffers::FlatBufferBuilder& fbb, const TripInfo* src) const
    {
        if (!src) {
            return {};
        }

        return CreateTripInfo(
            fbb,
            src->global_ref_id(),
            GetLineInfo(fbb, src->line())
        );
    }

    flatbuffers::Offset<LineInfo> TripInformationService::GetLineInfo(flatbuffers::FlatBufferBuilder& fbb, const LineInfo* src) const
    {
        if (!src) {
            return {};
        }

        return CreateLineInfo(
            fbb,
            src->global_ref_id(),
            src->type(),
            fbb.CreateString(src->name()),
            GetStopReference(fbb, src->headsign())
        );
    }

    flatbuffers::Offset<StopReference> TripInformationService::GetStopReference(flatbuffers::FlatBufferBuilder& fbb, const StopReference* src) const
    {
        if (!src) {
            return {};
        }

        return CreateStopReference(
            fbb,
            src->sequence_id(),
            src->global_ref_id(),
            fbb.CreateString(src->name())
        );
    }

    const TripRouteInfo* TripInformationService::GetTripRouteInfo() const
    {
        if (!m_HasData) {
            return nullptr;
        }

        return flatbuffers::GetRoot<TripRouteInfo>(m_MainFBB.GetBufferPointer());
    }

    const TripStateInfo* TripInformationService::GetTripStateInfo() const
    {
        auto tripRouteInfo = GetTripRouteInfo();
        return tripRouteInfo ? tripRouteInfo->trip() : nullptr;
    }

    void TripInformationService::Register(ServiceRegistry& registry)
    {
        registry.RegisterServiceCallback(
            Operation_GetTripRouteInfo,
            [&](const RequestPacket& request) -> OperationResult {
                std::lock_guard lock(m_DataMutex);
                if (!m_HasData) {
                    return 404;
                }
                return OperationResult(200, SerializeFlatBuffer(m_MainFBB));
            }
        );
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

    std::vector<std::string> TripInformationService::GetSpecifiedTariffSystemIDs(const std::string& tariffZones)
    {
        std::vector<std::string> systemIDs;

        for (auto zoneList : str_split(tariffZones, ';')) {
            auto parts = str_split(zoneList, ' ');
            if (parts.size() > 1) {
                systemIDs.push_back(parts[0]);
            }
        }

        return systemIDs;
    }

    std::string TripInformationService::GetZonesInTariffSystem(const std::string& tariffZones, const std::string& tariffSystemID)
    {
        std::string prefix = tariffSystemID + " ";

        for (auto zoneList : str_split(tariffZones, ';')) {
            if (zoneList.starts_with(prefix)) {
                return zoneList.substr(prefix.size());
            }
        }

        return "";
    }
}