#include "lwt_TripInfoAdvertiser.h"

#include <string>
#include "ISO8601.h"
#include "publisher_ssi_generated.h"
#include <esp_log.h>
#include <cstdio>
#include "FNVHash.h"
#include "lwt_TripInformationService.h"

namespace lwt {

    using namespace vdv301;
    using namespace IBIS_IP_CustomerInformationService_V2_3CZ1_0;

    TripInfoAdvertiser::TripInfoAdvertiser(SubscriberCIS& cisSubscriber, std::initializer_list<lwdn::Advertiser*> advertisers) :
        m_CISSubscriber{ cisSubscriber },
        m_Advertisers{ advertisers }
    {
        m_CISSubscriber.ObserveAllData(*this);
    }

    TripInfoAdvertiser::~TripInfoAdvertiser() {
        m_CISSubscriber.RemoveObserver(*this);
    }

    void TripInfoAdvertiser::OnDataChanged(const SubscriberCIS::AllData* result)
    {
        if (!result) {
            for (auto* advertiser : m_Advertisers) {
                advertiser->Stop();
            }
        }
        else {
            AdvDataBasic basicData = CreateBasicAdvData(*result);
            AdvDataExtended extData = CreateExtendedAdvData(basicData, *result);

            UpdateLegacyData(basicData);
            UpdateExtendedData(extData);

            // dump hex of extended data
            for (size_t i = 0; i < m_ExtDataBuffer.size(); ++i) {
                printf("%02X ", m_ExtDataBuffer[i]);
                if (i % 16 == 15) {
                    printf("\n");
                }
            }
            printf("\n");

            ESP_LOGI("TripInfoAdvertiser", "Updated advertisement data; legacy size=%zu, extended size=%zu", m_LegacyDataBuffer.size(), m_ExtDataBuffer.size());

            for (auto* advertiser : m_Advertisers) {
                ESP_LOGI("TripInfoAdvertiser", "Advertiser %p max size=%zu", advertiser, advertiser->GetMaxAdvDataSize());
                if (advertiser->GetMaxAdvDataSize() < m_ExtDataBuffer.size()) {
                    advertiser->SetLwdnAdvData(m_LegacyDataBuffer);
                }
                else {
                    advertiser->SetLwdnAdvData(m_ExtDataBuffer);
                }
                advertiser->Start();
            }
        }
    }

    AdvDataBasic TripInfoAdvertiser::CreateBasicAdvData(const SubscriberCIS::AllData& result)
    {
        AdvDataBasic legacyData;
        memset(&legacyData, 0, sizeof(legacyData));

        legacyData.line_type = result.VehicleMode ? TripInformationService::VehicleModeToLineType(*result.VehicleMode) : LineType::LineType_GenericBus;

        const TripInformationStructure* tripInfo = SubscriberCIS::GetTripInformationFromAllData(result);

        if (tripInfo) {
            if (!SubscriberCIS::IsTripRefPresent(*tripInfo)) {
                legacyData.trip_number = 0;
            }
            else {
                legacyData.trip_number = std::stoi(tripInfo->TripRef.Value);
            }

            const StopInformationStructure* stop = SubscriberCIS::GetCurrentStopFromAllData(result);

            if (stop) {
                if (!stop->DisplayContent.empty()) {
                    auto&& displayContent = stop->DisplayContent.front();
                    if (displayContent.LineInformation.LineNumber) {
                        legacyData.line_license_number = displayContent.LineInformation.LineNumber->Value;
                    }
                    legacyData.direction_cis_number = FindCisNumberByRef(displayContent.Destination.DestinationRef.Value, result);
                }

                legacyData.stop_cis_number = std::stoi(stop->GlobalStopRef.Value);

                if (tripInfo->LocationState) {
                    if (*tripInfo->LocationState == LocationStateEnumeration::AtStop) {
                        legacyData.flags |= AdvDataBasic::FLAG_IS_AT_STOP;
                    }
                }

                if (stop->ArrivalScheduled) {
                    legacyData.stop_arrival_time = LocalDateTime::parse(stop->ArrivalScheduled->Value).time.to_minute_of_day();
                }
                else {
                    legacyData.stop_arrival_time = -1;
                }
                if (stop->DepartureScheduled) {
                    legacyData.stop_departure_time = LocalDateTime::parse(stop->DepartureScheduled->Value).time.to_minute_of_day();
                }
                else {
                    legacyData.stop_departure_time = -1;
                }
            }

            if (tripInfo->TimetableDelay) {
                legacyData.delay = tripInfo->TimetableDelay->Value;
            }
        }

        return legacyData;
    }

    AdvDataExtended TripInfoAdvertiser::CreateExtendedAdvData(const AdvDataBasic& basicData, const SubscriberCIS::AllData& result)
    {
        AdvDataExtended extData(basicData);

        if (!result.TripInformation.empty()) {
            const StopInformationStructure* curStop = SubscriberCIS::GetCurrentStopFromAllData(result);

            if (curStop) {
                extData.cur_stop_name = curStop->StopName.empty() ? InternationalTextType_Value_t{} : curStop->StopName.front().Value;

                const DisplayContentStructure* displayContent = SubscriberCIS::FindDisplayContent("Interior", *curStop);

                if (displayContent) {
                    auto&& dest = displayContent->Destination;
                    extData.headsign = dest.DestinationName.empty() ? InternationalTextType_Value_t{} : dest.DestinationName.front().Value;
                    auto&& lineInfo = displayContent->LineInformation;
                    extData.line_name = lineInfo.LineName.empty() ? InternationalTextType_Value_t{} : lineInfo.LineName.front().Value;
                }
            }
        }

        return extData;
    }

    void TripInfoAdvertiser::UpdateLegacyData(const AdvDataBasic& legacyData)
    {
        legacyData.pack(m_LegacyDataBuffer.data());
    }

    void TripInfoAdvertiser::UpdateExtendedData(const AdvDataExtended& extData)
    {
        m_ExtDataBuffer.resize(extData.calc_size());
        extData.pack(m_ExtDataBuffer.data());
    }

    uint32_t TripInfoAdvertiser::FindCisNumberByRef(const std::string& ref, const SubscriberCIS::AllData& result)
    {
        auto stop = SubscriberCIS::FindStopByRef(ref, result);
        if (stop) {
            return std::stoi(stop->GlobalStopRef.Value);
        }
        return 0;
    }
}