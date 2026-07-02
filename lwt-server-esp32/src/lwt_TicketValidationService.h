#pragma once

#include "vdv_SubscriberTVS.h"
#include "flatbuffer_util.h"
#include "lwt_TripInformationService.h"
#include "ticket_validation_generated.h"
#include <string>
#include "lwt_ServiceRegistry.h"

namespace lwt {

    class TicketValidationService : Observer<TripRouteInfo>, Observer<vdv301::SubscriberTVS::CurrentTariffStop>, Observer<vdv301::SubscriberTVS::RazziaState>
    {
    private:
        std::string m_TariffSystemID;
        TripInformationService& m_TripInfoService;
        vdv301::SubscriberTVS* m_TVS;

        std::mutex m_DataMutex;
        bool m_HasData{ false };

        int32_t m_CurTripDelay{ 0 };
        int64_t m_TimeForTicketValidityStart{ 0 };
        std::string m_TariffZonesForValidation;
        std::vector<std::string> m_NextTariffZonesFromRoute;
        std::string m_NextTariffZonesForValidation;
        bool m_IsRazzia{ false };

        flatbuffers::FlatBufferBuilder m_ValidationInfoFBB{ PSRAMFlatBufferBuilder() };

    public:
        TicketValidationService(const std::string& tariffSystemId, TripInformationService& tripInfoService, vdv301::SubscriberTVS* tvsOpt = nullptr);
        virtual ~TicketValidationService() override;

        void Register(ServiceRegistry& registry);

        bool IsRazzia();

        virtual void OnChanged(const TripRouteInfo* result) override;
        virtual void OnChanged(const vdv301::SubscriberTVS::CurrentTariffStop* result) override;
        virtual void OnChanged(const vdv301::SubscriberTVS::RazziaState* result) override;

    private:
        void UpdateValidationInfo();
        void ResetValidationInfo();
        void FinishValidationInfo(flatbuffers::Offset<TicketValidationInfo> data);

        std::string GetTariffZonesOnlyMyTariffSystem(const std::string& tariffZones) const;

        static std::string ReduceNextTariffZones(const std::vector<std::string>& nextTariffZones, const std::string& curTariffZones);
    };
}