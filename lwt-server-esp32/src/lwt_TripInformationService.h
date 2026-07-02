#pragma once

#include "lwt_ServiceRegistry.h"
#include "vdv_SubscriberCIS.h"
#include "trip_information_generated.h"
#include "flatbuffer_util.h"
#include "Observable.h"
#include <mutex>

namespace lwt {

    class TripInformationService : public Observer<vdv301::SubscriberCIS::AllData>, Observable<TripStateInfo>, Observable<TripRouteInfo>
    {
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
         * @param fbb
         * @return flatbuffers::Offset<TripStateInfo>
         */
        flatbuffers::Offset<TripStateInfo> GetTripStateInfo(flatbuffers::FlatBufferBuilder& fbb);

        static LineType VehicleModeToLineType(IBIS_IP_CustomerInformationService_V2_3CZ1_0::VehicleModeEnumeration mode);
        static std::string BuildTariffZonesString(const IBIS_IP_CustomerInformationService_V2_3CZ1_0::StopInformationStructure& stopInfo);
        static std::vector<std::string> GetSpecifiedTariffSystemIDs(const std::string& tariffZones);
        static std::string GetZonesInTariffSystem(const std::string& tariffZones, const std::string& tariffSystemID);

    private:
        const TripRouteInfo* GetTripRouteInfo() const;
        const TripStateInfo* GetTripStateInfo() const;

        flatbuffers::Offset<TripInfo> GetTripInfo(flatbuffers::FlatBufferBuilder& fbb, const TripInfo* src) const;
        flatbuffers::Offset<LineInfo> GetLineInfo(flatbuffers::FlatBufferBuilder& fbb, const LineInfo* src) const;
        flatbuffers::Offset<StopReference> GetStopReference(flatbuffers::FlatBufferBuilder& fbb, const StopReference* src) const;
    };
}