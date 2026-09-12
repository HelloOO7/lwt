#pragma once

#include "lwdn_BleAdvertiser.h"
#include <functional>
#include "lwdn_Link.h"
#include "PSRAMContainers.h"
#include <optional>
#include "PSRAMTask.h"
#include <mutex>
#include "host/ble_gap.h"
#include <deque>
#include <condition_variable>
#include "PubSubTask.h"

namespace lwdn {

    class BleScanner : protected PubSubTask {
    public:
        using ServiceUUID = BleAdvertiser::ServiceUUID;

        struct ServiceData {
            ServiceUUID m_UUID;
            ByteVector m_Data;
        };

        struct ScanResult {
            LinkAddress m_Address;
            int8_t m_Rssi;
            psram_vector<ServiceData> m_ServiceData;
        };

        using ScanCallback = std::function<void(const ServiceData& adv)>;

        struct ScanConfig {
            ServiceUUID m_UUID;
            ScanCallback m_Callback;
        };

        using ScanHandle = size_t;

    private:
        struct ScanState {
            ScanHandle m_Handle;
            ScanConfig m_Config;
        };

        std::mutex m_Mutex;
        psram_vector<ScanState> m_Scans;
        size_t m_NextScanHandle{ 1 };
        bool m_Scanning{ false };
        bool m_ScanEnabled{ false };

        std::deque<ScanResult, psram_allocator<ScanResult>> m_PendingResults;
        std::condition_variable m_ResultsAvailable;

    public:
        BleScanner(size_t taskStackSize = 4096, int taskPriority = tskIDLE_PRIORITY + 1);

        void EnableScan();

        ScanHandle StartScan(const ScanConfig& config);
        ScanHandle StartScan(ScanConfig&& config);
        void StopScan(ScanHandle handle);

        virtual void ProcessData() override;

    private:
        void OnDiscovery(const ScanResult& result);
        void OnDiscovery(const ble_gap_disc_desc& desc);
        void OnDiscovery(const ble_gap_ext_disc_desc& desc);
        void OnDiscovery(const ble_gap_event* event);

        void ParseBleAddress(const ble_addr_t& addr, ScanResult& dest);
        void ParseAdvDataFields(const uint8_t* data, size_t length, ScanResult& dest);

        bool CheckScanFilter(const ScanConfig& config, const ScanResult& result);

        size_t NewScanHandle();
        void StartScanning();
        void StopScanning();

        void ResultDeliveryTask();

        static int BleDiscCallback(ble_gap_event* event, void* arg);
    };
}