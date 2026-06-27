#pragma once

#include "lwdn_Advertiser.h"
#include <initializer_list>
#include <vector>
#include "vdv_SubscriberCIS.h"
#include "flatbuffer_util.h"
#include "lwt_AdvData.h"
#include <array>

namespace lwt {

    class TripInfoAdvertiser : public vdv301::SubscriberObserver<vdv301::SubscriberCIS::AllData>
    {
    private:
        vdv301::SubscriberCIS& m_CISSubscriber;
        std::vector<lwdn::Advertiser*> m_Advertisers;

        std::array<uint8_t, AdvDataBasic::PACKED_SIZE> m_LegacyDataBuffer{};
        std::vector<uint8_t> m_ExtDataBuffer;

    public:
        TripInfoAdvertiser(vdv301::SubscriberCIS& cisSubscriber, std::initializer_list<lwdn::Advertiser*> advertisers);

        virtual void OnDataChanged(const vdv301::SubscriberCIS::AllData* result) override;

    private:
        static AdvDataBasic CreateBasicAdvData(const vdv301::SubscriberCIS::AllData& result);
        static AdvDataExtended CreateExtendedAdvData(const AdvDataBasic& basicData, const vdv301::SubscriberCIS::AllData& result);

        void UpdateLegacyData(const AdvDataBasic& result);
        void UpdateExtendedData(const AdvDataExtended& result);
        
        static uint32_t FindCisNumberByRef(const std::string& ref, const vdv301::SubscriberCIS::AllData& result);
        static size_t FindLastStopIndexByRef(const std::string& ref, const vdv301::SubscriberCIS::AllData& result);

        static LineType VehicleModeToLineType(IBIS_IP_CustomerInformationService_V2_3CZ1_0::VehicleModeEnumeration mode);

        static const IBIS_IP_CustomerInformationService_V2_3CZ1_0::DisplayContentStructure* FindDisplayContent(const std::string& displayContentRef, const IBIS_IP_CustomerInformationService_V2_3CZ1_0::StopInformationStructure& parent);
    };
}