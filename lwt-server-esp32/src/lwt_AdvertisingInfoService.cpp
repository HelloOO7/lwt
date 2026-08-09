#include "lwt_AdvertisingInfoService.h"

#include "adv_info_generated.h"
#include "operations_generated.h"
#include "lwt_ApplicationServer.h"

namespace lwt {

    AdvertisingInfoService::AdvertisingInfoService(TripInfoAdvertiser& advertiser) :
        m_Advertiser{ advertiser }
    {
    }

    lwt::AdvertisingChannel ConvertChannelTypeToFlatbuffer(TripInfoAdvertiser::ChannelType channelType) {
        switch (channelType) {
        case TripInfoAdvertiser::ChannelType::BLE_LEGACY:
            return lwt::AdvertisingChannel_BleLegacy;
        case TripInfoAdvertiser::ChannelType::BLE_EXTENDED:
            return lwt::AdvertisingChannel_BleExtended;
        case TripInfoAdvertiser::ChannelType::WIFI_NAN:
            return lwt::AdvertisingChannel_WifiNan;
        default:
            return lwt::AdvertisingChannel_BleLegacy;
        }
    }

    bool ShouldReturnChannel(const TripInfoAdvertiser::ChannelInfo& channelInfo, const AdvertisingInfoRequest& request) {
        if (request.channels() == nullptr || request.channels()->size() == 0) {
            return true;
        }
        for (auto channelType : *request.channels()) {
            if (ConvertChannelTypeToFlatbuffer(channelInfo.m_Type) == channelType) {
                return true;
            }
        }
        return false;
    }

    void AdvertisingInfoService::Register(ServiceRegistry& registry) {
        registry.RegisterServiceCallback(
            Operation_GetAdvertisingInfo,
            ApplicationServer::CreateOperationServiceFunc<AdvertisingInfoRequest>(
                [this](const AdvertisingInfoRequest& request, flatbuffers::FlatBufferBuilder& fbb) -> ResponseStatus {
                    std::vector<flatbuffers::Offset<lwt::AdvertisingInfo>> infoOffsets;

                    m_Advertiser.EnumerateAdvertisingChannels(
                        [&](const TripInfoAdvertiser::ChannelInfo& channelInfo) {
                            if (ShouldReturnChannel(channelInfo, request)) {
                                flatbuffers::Offset<flatbuffers::Vector<uint8_t>> advDataOffset = {};
                                if (request.include_data()) {
                                    advDataOffset = fbb.CreateVector(channelInfo.m_AdvData.data(), channelInfo.m_AdvData.size());
                                }

                                infoOffsets.push_back(
                                    lwt::CreateAdvertisingInfo(
                                        fbb,
                                        ConvertChannelTypeToFlatbuffer(channelInfo.m_Type),
                                        fbb.CreateVector(channelInfo.m_MACAddress.data(), channelInfo.m_MACAddress.size()),
                                        advDataOffset
                                    )
                                );
                            }
                        }
                    );

                    fbb.Finish(
                        lwt::CreateAdvertisingInfoResponse(
                            fbb,
                            fbb.CreateVector(infoOffsets)
                        )
                    );

                    return 200;
                }
            )
        );
    }
}