#include "lwdn_BleAdvertiser.h"

#include "BitConverter.h"
#include "Overloaded.h"

#include "host/ble_hs_adv.h"
#include "services/gap/ble_svc_gap.h"
#include <vector>
#include <algorithm>

namespace lwdn {

    BleAdvertiser::BleAdvertiser(InstanceID instanceID, const ServiceUUID& serviceID, Flags flags) :
        m_InstanceID{ instanceID },
        m_ServiceID{ serviceID },
        m_Flags{ flags }
    {
        memset(&m_AdvParams, 0, sizeof(m_AdvParams));

        m_AdvParams.connectable = true;
        if ((flags & Flags::USE_LEGACY_ADVERTISING) != Flags::NONE) {
            m_AdvParams.legacy_pdu = true;
            m_AdvParams.scannable = true;
        }
        m_AdvParams.anonymous = false;
        m_AdvParams.own_addr_type = BLE_OWN_ADDR_PUBLIC;
        m_AdvParams.primary_phy = BLE_HCI_LE_PHY_1M;
        m_AdvParams.secondary_phy = BLE_HCI_LE_PHY_2M;
        m_AdvParams.sid = instanceID;
        m_AdvParams.tx_power = 127;

        int rc = ble_gap_ext_adv_configure(m_InstanceID, &m_AdvParams, 0, GapEventCallback, this);
        if (rc != 0) {
            MODLOG_DFLT(ERROR, "error configuring advertisement; iid=%d rc=%d\n", m_InstanceID, rc);
            return;
        }
    }

    void BleAdvertiser::Start() {
        Readvertise();
    }

    void BleAdvertiser::Stop() {
        if (IsAdvertising()) {
            int rc = ble_gap_ext_adv_stop(m_InstanceID);
            if (rc != 0) {
                MODLOG_DFLT(ERROR, "error stopping advertisement; iid=%d rc=%d\n", m_InstanceID, rc);
                return;
            }
        }
    }

    bool BleAdvertiser::IsAdvertising() const {
        return ble_gap_ext_adv_active(m_InstanceID);
    }

    BleAdvertiser::Flags BleAdvertiser::GetFlags() const {
        return m_Flags;
    }

    int BleAdvertiser::HandleGapEvent(ble_gap_event* event) {
        switch (event->type) {
        case BLE_GAP_EVENT_CONNECT:
            /* A new connection was established or a connection attempt failed. */
            if (event->connect.status == 0) {
                MODLOG_DFLT(INFO, "connection established; handle=%d\n", event->connect.conn_handle);
            }
            else {
                MODLOG_DFLT(INFO, "connection failed; status=%d\n", event->connect.status);
            }
            Readvertise();
            break;

        case BLE_GAP_EVENT_DISCONNECT:
            MODLOG_DFLT(INFO, "disconnect; reason=%d\n", event->disconnect.reason);
            Readvertise();
            break;

        case BLE_GAP_EVENT_ADV_COMPLETE:
            MODLOG_DFLT(INFO, "advertise complete\n");
            Readvertise();
            break;

        default:
            break;
        }
        return 0;
    }

    void BleAdvertiser::Readvertise() {
        if (!IsAdvertising()) {
            int rc = ble_gap_ext_adv_start(m_InstanceID, 0, 0);
            if (rc != 0) {
                MODLOG_DFLT(ERROR, "error enabling advertisement; iid=%d rc=%d\n", m_InstanceID, rc);
                return;
            }
        }
    }

    int BleAdvertiser::GapEventCallback(ble_gap_event* event, void* arg)
    {
        BleAdvertiser* advertiser = static_cast<BleAdvertiser*>(arg);
        return advertiser->HandleGapEvent(event);
    }

    bool BleAdvertiser::SetLwdnAdvData(const ByteSpan& data) {
        ByteVector advData;
        advData.reserve(GetRawFieldsMaxSize());

        // we can not use ble_hs_adv_set_fields_mbuf, because for extended advertising,
        // the service data may be longer than 256 bytes, but the ble_hs_adv_fields struct uses
        // uint8_t for the length of the service data, which limits it to 255 bytes.

        if ((m_Flags & Flags::INCLUDE_DEVICE_NAME) != Flags::NONE) {
            const char* name = ble_svc_gap_device_name();

            advData.push_back(strlen(name) + 1); // length of the name + 1 byte for the type
            advData.push_back(BLE_HS_ADV_TYPE_COMP_NAME); // type for complete name
            advData.insert(advData.end(), name, name + strlen(name));
        }

        int advType = GetServiceDataAdvType(m_ServiceID);
        auto uuidSize = GetServiceUUIDSize(m_ServiceID);

        size_t chunkMaxInnerSize = 255 - (uuidSize + 1); // 1 byte for the type

        // we must split the data to <= 255 byte chunks, as the length field is only 1 byte
        // the client is responsible for reassembling the data from the scan record.

        for (size_t dataOffset = 0; dataOffset < data.size(); dataOffset += chunkMaxInnerSize) {
            size_t chunkSize = std::min(data.size() - dataOffset, chunkMaxInnerSize);
            ByteSpan chunk = data.subspan(dataOffset, chunkSize);

            advData.push_back(uuidSize + 1 + chunkSize); // length of the service data + 1 byte for the type
            advData.push_back(advType); // type for service data
            advData.resize(advData.size() + uuidSize); // reserve space for the UUID
            uint8_t* uuidPtr = advData.data() + advData.size() - uuidSize;

            using BC = BitConverter<std::endian::little>;
            std::visit(
                overloaded{
                    [&](UUID16 u16) {
                        BC::FromInt16(u16, uuidPtr);
                    },
                    [&](UUID32 u32) {
                        BC::FromInt32(u32, uuidPtr);
                    },
                    [&](const UUID128& u128) {
                        std::copy(u128.begin(), u128.end(), uuidPtr);
                    }
                },
                m_ServiceID
            );

            advData.insert(advData.end(), chunk.begin(), chunk.end());
        }

        os_mbuf* bleAdvData = os_msys_get_pkthdr(GetRawFieldsMaxSize(), 0);
        assert(bleAdvData);

        os_mbuf_copyinto(bleAdvData, 0, advData.data(), advData.size());

        bool needReadvertise = advData.size() > BLE_HCI_MAX_EXT_ADV_DATA_LEN;

        if (needReadvertise) {
            Stop();
        }

        bool result = true;

        int rc = ble_gap_ext_adv_set_data(m_InstanceID, bleAdvData);
        if (rc != 0) {
            MODLOG_DFLT(ERROR, "error setting advertisement data; iid=%d rc=%d, size=%zu\n", m_InstanceID, rc, advData.size());
            result = false;
        }

        if (needReadvertise) {
            Readvertise();
        }

        return result;
    }

    size_t BleAdvertiser::GetRawFieldsMaxSize() const {
        size_t max;
        if ((m_Flags & Flags::USE_LEGACY_ADVERTISING) != Flags::NONE) {
            max = BLE_HS_ADV_MAX_SZ;
        }
        else {
            // more than this will be rejected by controller with BLE_ERR_PACKET_TOO_LONG in connectable mode
            // or dropped by Android in non-connectable mode (most likely unsupported)
            max = 245;
        }
        return max;
    }

    size_t BleAdvertiser::GetMaxAdvDataSize() const {
        size_t max = GetRawFieldsMaxSize();
        size_t baseData = 0;

        if ((m_Flags & Flags::INCLUDE_DEVICE_NAME) != Flags::NONE) {
            baseData += 2; // 1 byte for length, 1 byte for type
            baseData += strlen(ble_svc_gap_device_name());
        }

        baseData += 2; //uuid - 1 byte for length, 1 byte for type
        baseData += GetServiceUUIDSize(m_ServiceID);

        if (max < baseData) {
            return 0;
        }
        return max - baseData;
    }

    size_t BleAdvertiser::GetServiceUUIDSize(const ServiceUUID& uuid) {
        return std::visit(
            overloaded{
                [](UUID16) { return sizeof(UUID16); },
                [](UUID32) { return sizeof(UUID32); },
                [](const UUID128& u128) { return u128.size(); }
            },
            uuid
        );
    }

    int BleAdvertiser::GetServiceDataAdvType(const ServiceUUID& uuid) {
        return std::visit(
            overloaded{
                [](UUID16) { return BLE_HS_ADV_TYPE_SVC_DATA_UUID16; },
                [](UUID32) { return BLE_HS_ADV_TYPE_SVC_DATA_UUID32; },
                [](const UUID128&) { return BLE_HS_ADV_TYPE_SVC_DATA_UUID128; }
            },
            uuid
        );
    }
}