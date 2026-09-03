#pragma once

#include "lwt_ServiceRegistry.h"
#include "vdv_SubscriberCIS.h"
#include "trip_information_generated.h"
#include "flatbuffer_util.h"
#include "Observable.h"
#include <mutex>
#include "StringExtensions.h"

namespace lwt {

    class TripInformationService : public Observer<vdv301::SubscriberCIS::AllData>, Observable<TripStateInfo>, Observable<TripRouteInfo>
    {
    public:
        struct TripStateInfoResult {
            flatbuffers::Offset<TripStateInfo> OfsTripState;
            flatbuffers::Offset<TripStopInfo> OfsCurrentStop;
        };

    private:
        vdv301::SubscriberCIS& m_CIS;

        std::recursive_mutex m_DataMutex;
        bool m_HasData{ false };
        flatbuffers::FlatBufferBuilder m_MainFBB{ PSRAMFlatBufferBuilder() };

    public:
        TripInformationService(vdv301::SubscriberCIS& cis);
        ~TripInformationService();

        void Register(ServiceRegistry& registry);

        virtual void OnChanged(const vdv301::SubscriberCIS::AllData* result) override;

        void ObserveTripStateInfo(Observer<TripStateInfo>& observer);
        void ObserveTripRouteInfo(Observer<TripRouteInfo>& observer);

        void RemoveObserver(Observer<TripStateInfo>& observer);
        void RemoveObserver(Observer<TripRouteInfo>& observer);

        /**
         * @brief Copy the TripStateInfo held by the service into another FlatBufferBuilder.
         *
         * @param fbb FlatBufferBuilder to copy the TripStateInfo into.
         * @param fbbCurrentStop FlatBufferBuilder to copy current stop information into.
         * @return TripStateInfoResult
         */
        TripStateInfoResult GetTripStateInfoEx(flatbuffers::FlatBufferBuilder* fbbTripState, flatbuffers::FlatBufferBuilder* fbbCurrentStop);
        flatbuffers::Offset<TripStateInfo> GetTripStateInfo(flatbuffers::FlatBufferBuilder& fbb);

        static LineType VehicleModeToLineType(IBIS_IP_CustomerInformationService_V2_3CZ1_0::VehicleModeEnumeration mode);

        template<typename TStopInformationStructure>
        static std::string BuildTariffZonesString(const TStopInformationStructure& stopInfo);
        static std::vector<std::string> GetSpecifiedTariffSystemIDs(const std::string& tariffZones);
        static std::string GetZonesInTariffSystem(const std::string& tariffZones, const std::string& tariffSystemID);

    private:
        const TripRouteInfo* GetTripRouteInfo() const;
        const TripStateInfo* GetTripStateInfo() const;

        flatbuffers::Offset<TripInfo> GetTripInfo(flatbuffers::FlatBufferBuilder& fbb, const TripInfo* src) const;
        flatbuffers::Offset<LineInfo> GetLineInfo(flatbuffers::FlatBufferBuilder& fbb, const LineInfo* src) const;
        flatbuffers::Offset<StopReference> GetStopReference(flatbuffers::FlatBufferBuilder& fbb, const StopReference* src) const;
        flatbuffers::Offset<TripStopInfo> GetTripStopInfo(flatbuffers::FlatBufferBuilder& fbb, const TripStopInfo* src) const;
    };

    template<typename TStopInformationStructure>
    std::string TripInformationService::BuildTariffZonesString(const TStopInformationStructure& stopInfo) {
        std::string zones;
        for (const auto& zone : stopInfo.FareZone) {
            if (!zones.empty()) {
                zones += ";";
            }
            if (zone.Value.contains(":")) {
                // TVS sends IDS information correctly (as a global identifier), but we are already used
                // to the "displayable" convention of using a gap, so convert it
                zones += str_replace(zone.Value, ":", " ");
            } else {
                zones += zone.Value;
            }
        }
        return zones;
    }
}