#pragma once

#include "lwdn_BleScanner.h"
#include "cico_generated.h"
#include <array>
#include "Observable.h"
#include "UUID.h"
#include <mutex>
#include "TimerProc.h"

namespace lwt {

    class PresenceTracker : public Observable<UUID> {
    private:
        using ClientID = std::array<uint8_t, 8>;
        using ClientSecret = std::array<uint8_t, 32>;

        struct Client {
            ClientID m_ClientId;
            ClientSecret m_TotpSecret;
            int64_t m_TotpPeriodMs;
            int64_t m_LastSeenUptime;
            int64_t m_LastSeenUnix;
            UUID m_SessionId;
        };

        lwdn::BleScanner& m_Scanner;
        lwdn::BleScanner::ScanHandle m_ScanHandle;

        std::mutex m_Mutex;
        std::vector<Client> m_Clients;
        size_t m_Capacity;
        int64_t m_TimeToVanishMs{ 0 };

        TimerProc m_NextWakeupTimer;

    public:
        PresenceTracker(lwdn::BleScanner& scanner, const lwdn::BleScanner::ServiceUUID& serviceUuid, size_t capacity, int64_t timeToVanishMs);
        ~PresenceTracker();

        void RegisterClient(const PresenceTrackingConfig& config, const UUID& sessionId);
        void UnregisterClient(const UUID& sessionId);

        void ObserveClientVanished(Observer<UUID>& observer);
        void RemoveObserver(Observer<UUID>& observer);

    private:
        void HandleAdvertisement(const lwdn::BleScanner::ServiceData& adv);
        void RefreshClientPresent(const ClientID& clientId, uint32_t givenTotpValue, int64_t uptimeMs, int64_t unixMs);
        void UpdateClientLastSeen(Client& client, int64_t uptimeMs, int64_t unixMs);
        void UpdateVanishedClients();
        bool IsVanished(const Client& client, int64_t uptimeMs) const;
        void RecalculateNextWakeup();
        bool UpdateExistingClient(const ClientID& clientId, const UUID& sessionId);
    };
}