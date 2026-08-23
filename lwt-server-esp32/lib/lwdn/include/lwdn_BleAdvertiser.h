#pragma once

#include "lwdn_Advertiser.h"
#include <cstdint>
#include <variant>
#include "host/ble_gap.h"
#include "lwdn_BleLink.h"
#include "EnumBitflags.h"
#include "CommonTypes.h"

namespace lwdn {

    class BleAdvertiser : public Advertiser
    {
    public:
        enum class Flags {
            NONE = 0,
            INCLUDE_DEVICE_NAME = (1 << 0),
            USE_LEGACY_ADVERTISING = (1 << 1),
        };

        using InstanceID = uint8_t;

        using UUID16 = uint16_t;
        using UUID32 = uint32_t;
        using UUID128 = std::array<uint8_t, 16>;
        using ServiceUUID = std::variant<UUID16, UUID32, UUID128>;

    private:
        InstanceID m_InstanceID{ 0 };
        ServiceUUID m_ServiceID{};
        Flags m_Flags{ Flags::NONE };
        ble_gap_ext_adv_params m_AdvParams{};

    public:
        BleAdvertiser(InstanceID instanceID, const ServiceUUID& serviceID, Flags flags = Flags::NONE);

        virtual ~BleAdvertiser() = default;

        virtual void Start() override;
        virtual void Stop() override;
        virtual bool IsAdvertising() const override;

        Flags GetFlags() const;

        virtual size_t GetMaxAdvDataSize() const override;
        virtual bool SetLwdnAdvData(const ByteSpan& data) override;

        virtual LinkAdapter* GetLinkAdapter() const override { return &BLE_ADAPTER; }

    private:
        size_t GetRawFieldsMaxSize() const;
        void Readvertise();

        int HandleGapEvent(ble_gap_event* event);

        static int GapEventCallback(ble_gap_event* event, void* arg);

        static size_t GetServiceUUIDSize(const ServiceUUID& uuid);
        static int GetServiceDataAdvType(const ServiceUUID& uuid);
    };

    DEFINE_ENUM_FLAG_OPERATORS(BleAdvertiser::Flags);
}