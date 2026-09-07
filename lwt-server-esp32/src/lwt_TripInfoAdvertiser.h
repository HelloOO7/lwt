#pragma once

#include "lwdn_Advertiser.h"
#include <initializer_list>
#include <vector>
#include "vdv_SubscriberCIS.h"
#include "flatbuffer_util.h"
#include "lwt_AdvData.h"
#include "CommonTypes.h"
#include "PSRAMContainers.h"
#include "lwt_TicketValidationService.h"
#include "lwt_CicoService.h"
#include <array>
#include <functional>
#include <mutex>

namespace lwt {

    class TripInfoAdvertiser :
        Observer<vdv301::SubscriberCIS::AllData>, Observer<TicketValidationState>, Observer<CicoState>
    {
    public:
        enum class ChannelType {
            BLE_LEGACY,
            BLE_EXTENDED,
            WIFI_NAN
        };

        struct ChannelInfo {
            ChannelType m_Type;
            ByteSpan m_MACAddress;
            ByteSpan m_AdvData;
        };

    private:
        vdv301::SubscriberCIS& m_CISSubscriber;
        TicketValidationService& m_TicketValidationService;
        CicoService& m_CicoService;
        std::vector<lwdn::Advertiser*> m_Advertisers;

        std::mutex m_DataMutex;
        AdvDataExtended m_Data;
        std::array<uint8_t, AdvDataBasic::PACKED_SIZE> m_LegacyDataBuffer{};
        ByteVector m_ExtDataBuffer;

    public:
        TripInfoAdvertiser(
            vdv301::SubscriberCIS& cisSubscriber,
            TicketValidationService& ticketValidationService, CicoService& cicoService,
            std::initializer_list<lwdn::Advertiser*> advertisers
        );
        ~TripInfoAdvertiser();

        virtual void OnChanged(const vdv301::SubscriberCIS::AllData* result) override;
        virtual void OnChanged(const TicketValidationState* result) override;
        virtual void OnChanged(const CicoState* result) override;

        void EnumerateAdvertisingChannels(std::function<void(const ChannelInfo&)> callback);

    private:
        static AdvDataBasic CreateBasicAdvData(const vdv301::SubscriberCIS::AllData& result);
        static AdvDataExtended CreateExtendedAdvData(const AdvDataBasic& basicData, const vdv301::SubscriberCIS::AllData& result);

        bool IsUseExtendedDataForAdvertiser(const lwdn::Advertiser* advertiser) const;

        void UpdateLegacyData(const AdvDataBasic& result);
        void UpdateExtendedData(const AdvDataExtended& result);
        void UpdateDataBuffers();
        void PublishToAdvertisers();

        static uint32_t FindCisNumberByRef(const std::string& ref, const vdv301::SubscriberCIS::AllData& result);
    };
}