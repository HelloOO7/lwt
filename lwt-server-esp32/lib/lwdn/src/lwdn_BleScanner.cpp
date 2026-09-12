#include "lwdn_BleScanner.h"

#include "host/ble_hs_adv.h"
#include "host/ble_gap.h"
#include "esp_log.h"
#include "BitConverter.h"
#include <cstdio>

namespace lwdn {

    static constexpr const char* TAG = "BleScanner";

    BleScanner::BleScanner(size_t taskStackSize, int taskPriority)
        : PubSubTask("BleScanner", taskStackSize, taskPriority)
    {

    }

    BleScanner::ScanHandle BleScanner::StartScan(const ScanConfig& config)
    {
        std::lock_guard lock(m_Mutex);

        auto handle = NewScanHandle();
        m_Scans.push_back(ScanState{ handle, config });
        StartScanning();

        return handle;
    }

    BleScanner::ScanHandle BleScanner::StartScan(ScanConfig&& config)
    {
        std::lock_guard lock(m_Mutex);

        auto handle = NewScanHandle();
        m_Scans.push_back(ScanState{ handle, std::move(config) });
        StartScanning();

        return handle;
    }

    void BleScanner::StopScan(ScanHandle handle)
    {
        std::lock_guard lock(m_Mutex);

        std::erase_if(m_Scans, [handle](const ScanState& scan) { return scan.m_Handle == handle; });

        if (m_Scans.empty()) {
            StopScanning();
        }
    }

    size_t BleScanner::NewScanHandle()
    {
        return m_NextScanHandle++;
    }

    void BleScanner::StartScanning() {
        if (m_Scanning) {
            return;
        }
        if (!m_ScanEnabled) {
            return;
        }
        m_Scanning = true;
        ble_gap_ext_disc_params params{
            .itvl = BLE_GAP_SCAN_ITVL_MS(70),
            .window = BLE_GAP_SCAN_WIN_MS(30),
            .passive = true,
            .disable_observer_mode = false
        };
        int err = ble_gap_ext_disc(BLE_OWN_ADDR_PUBLIC, 0, 0, false, 0, false, &params, nullptr, BleDiscCallback, this);
        if (err != 0) {
            ESP_LOGE(TAG, "Failed to start BLE scanning: %d", err);
        }
    }

    void BleScanner::StopScanning() {
        if (!m_Scanning) {
            return;
        }
        m_Scanning = false;
        ble_gap_disc_cancel();
    }

    void BleScanner::EnableScan()
    {
        std::lock_guard lock(m_Mutex);
        m_ScanEnabled = true;
        if (!m_Scans.empty()) {
            StartScanning();
        }
    }

    void BleScanner::ProcessData()
    {
        for (const auto& result : m_PendingResults) {
            for (const auto& scan : m_Scans) {
                if (CheckScanFilter(scan.m_Config, result)) {
                    for (auto&& sd : result.m_ServiceData) {
                        if (sd.m_UUID == scan.m_Config.m_UUID) {
                            scan.m_Config.m_Callback(sd);
                        }
                    }
                }
            }
        }
        m_PendingResults.clear();
    }

    void BleScanner::OnDiscovery(const ScanResult& result)
    {
        bool anyMatch = false;
        for (const auto& scan : m_Scans) {
            if (CheckScanFilter(scan.m_Config, result)) {
                anyMatch = true;
            }
        }
        if (anyMatch) {
            std::unique_lock lock(m_PubSubMutex);
            while (m_PendingResults.size() >= 128) {
                // sanity limit, so that the queue is not overloaded
                m_PendingResults.pop_front();
            }
            m_PendingResults.push_back(result);
            SignalDataReady();
        }
    }

    bool BleScanner::CheckScanFilter(const ScanConfig& config, const ScanResult& result)
    {
        for (const auto& serviceData : result.m_ServiceData) {
            if (serviceData.m_UUID == config.m_UUID) {
                return true;
            }
        }
        return false;
    }

    void BleScanner::OnDiscovery(const ble_gap_disc_desc& desc)
    {
        ScanResult res;
        ParseBleAddress(desc.addr, res);
        res.m_Rssi = desc.rssi;

        ParseAdvDataFields(desc.data, desc.length_data, res);

        OnDiscovery(res);
    }

    void BleScanner::OnDiscovery(const ble_gap_ext_disc_desc& desc)
    {
        if (desc.data_status != BLE_GAP_EXT_ADV_DATA_STATUS_COMPLETE) {
            return;
        }

        ScanResult res;
        ParseBleAddress(desc.addr, res);
        res.m_Rssi = desc.rssi;

        ParseAdvDataFields(desc.data, desc.length_data, res);

        OnDiscovery(res);
    }

    void BleScanner::ParseBleAddress(const ble_addr_t& addr, ScanResult& dest)
    {
        memcpy(dest.m_Address.data(), addr.val, dest.m_Address.size());
        std::reverse(dest.m_Address.begin(), dest.m_Address.end()); //nimble
    }

    void BleScanner::ParseAdvDataFields(const uint8_t* data, size_t length, ScanResult& dest)
    {
        ble_hs_adv_fields fields{};
        int rc = ble_hs_adv_parse_fields(&fields, data, length);
        if (rc != 0) {
            if (rc != BLE_HS_EBADDATA) { // this happens too often to log
                ESP_LOGE(TAG, "Failed to parse advertisement fields: %d", rc);
            }
            return;
        }

        using BC = BitConverter<std::endian::little>;

        if (fields.svc_data_uuid16) {
            ServiceData sd;
            sd.m_UUID = BC::ToUInt16(fields.svc_data_uuid16);
            sd.m_Data.assign(fields.svc_data_uuid16 + sizeof(uint16_t), fields.svc_data_uuid16 + fields.svc_data_uuid16_len);
            dest.m_ServiceData.push_back(sd);
        }
        if (fields.svc_data_uuid32) {
            ServiceData sd;
            sd.m_UUID = BC::ToUInt32(fields.svc_data_uuid32);
            sd.m_Data.assign(fields.svc_data_uuid32 + sizeof(uint32_t), fields.svc_data_uuid32 + fields.svc_data_uuid32_len);
            dest.m_ServiceData.push_back(sd);
        }
        if (fields.svc_data_uuid128) {
            ServiceData sd;
            BleAdvertiser::UUID128 uuid;
            std::copy(fields.svc_data_uuid128, fields.svc_data_uuid128 + uuid.size(), uuid.begin());
            sd.m_Data.assign(fields.svc_data_uuid128 + uuid.size(), fields.svc_data_uuid128 + fields.svc_data_uuid128_len);
            dest.m_ServiceData.push_back(sd);
        }
    }

    void BleScanner::OnDiscovery(const ble_gap_event* event)
    {
        std::lock_guard lock(m_Mutex);

        if (event->type == BLE_GAP_EVENT_DISC) {
            OnDiscovery(event->disc);
        }
        else if (event->type == BLE_GAP_EVENT_EXT_DISC) {
            OnDiscovery(event->ext_disc);
        }
    }

    int BleScanner::BleDiscCallback(ble_gap_event* event, void* arg)
    {
        BleScanner* scanner = static_cast<BleScanner*>(arg);
        scanner->OnDiscovery(event);
        return 0;
    }
}