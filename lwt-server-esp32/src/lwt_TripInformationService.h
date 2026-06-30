#pragma once

#include "lwt_ServiceRegistry.h"
#include "vdv_SubscriberCIS.h"
#include "trip_information_generated.h"
#include "flatbuffer_util.h"
#include <mutex>

namespace lwt {

    class TripInformationService : public vdv301::SubscriberObserver<vdv301::SubscriberCIS::AllData>
    {
    private:
        vdv301::SubscriberCIS& m_CIS;

        std::mutex m_DataMutex;
        bool m_HasData{ false };
        flatbuffers::FlatBufferBuilder m_MainFBB{ PSRAMFlatBufferBuilder() };

    public:
        TripInformationService(vdv301::SubscriberCIS& cis);
        ~TripInformationService();

        void Register(ServiceRegistry& registry);

        virtual void OnDataChanged(const vdv301::SubscriberCIS::AllData* result) override;

        static LineType VehicleModeToLineType(IBIS_IP_CustomerInformationService_V2_3CZ1_0::VehicleModeEnumeration mode);
    };
}