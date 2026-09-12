#include "lwt_PresenceTracker.h"

#include "SystemTime.h"
#include "BitConverter.h"
#include <algorithm>
#include "TOTP.h"
#include "esp_log.h"

namespace lwt {

    static constexpr const char* TAG = "PresenceTracker";

    PresenceTracker::PresenceTracker(lwdn::BleScanner& scanner, const lwdn::BleScanner::ServiceUUID& serviceUuid, size_t capacity, int64_t timeToVanishMs) :
        m_Scanner(scanner),
        m_Capacity(capacity),
        m_TimeToVanishMs(timeToVanishMs),
        m_NextWakeupTimer(
            [this]() {
                std::lock_guard lock(m_Mutex);
                UpdateVanishedClients();
            },
            0
        )
    {
        m_ScanHandle = m_Scanner.StartScan(
            lwdn::BleScanner::ScanConfig{
                .m_UUID = serviceUuid,
                .m_Callback = [this](const lwdn::BleScanner::ServiceData& adv) {
                    HandleAdvertisement(adv);
                }
            }
        );
    }

    PresenceTracker::~PresenceTracker() {
        m_Scanner.StopScan(m_ScanHandle);
    }

    void PresenceTracker::RegisterClient(const PresenceTrackingConfig& config, const UUID& sessionId) {
        std::lock_guard lock(m_Mutex);

        bool firstClient = m_Clients.empty();

        Client client;
        std::copy(config.client_id()->begin(), config.client_id()->end(), client.m_ClientId.begin());

        if (UpdateExistingClient(client.m_ClientId, sessionId)) {
            return; // session already registered, so just refresh its ID
        }

        std::copy(config.totp_secret()->begin(), config.totp_secret()->end(), client.m_TotpSecret.begin());
        client.m_TotpPeriodMs = config.totp_period();
        client.m_LastSeenUnix = SystemTime::EpochMillis();
        client.m_LastSeenUptime = SystemTime::UptimeMillis();
        client.m_SessionId = sessionId;
        m_Clients.push_back(std::move(client));

        ESP_LOGI(TAG, "Registered client session: %s", sessionId.ToString().c_str());

        // next wakeup can not change here, as this will always be the latest client.
        // however, we may need to start the task

        if (firstClient) {
            m_NextWakeupTimer.Restart((client.m_LastSeenUptime + m_TimeToVanishMs) * 1000); // convert to microseconds
        }
    }

    void PresenceTracker::UnregisterClient(const UUID& sessionId) {
        std::lock_guard lock(m_Mutex);

        std::erase_if(
            m_Clients,
            [&sessionId](const Client& client) {
                return client.m_SessionId == sessionId;
            }
        );

        RecalculateNextWakeup();
    }

    bool PresenceTracker::UpdateExistingClient(const ClientID& clientId, const UUID& sessionId) {
        auto clientIt = std::find_if(
            m_Clients.begin(),
            m_Clients.end(),
            [&sessionId](const Client& client) {
                // client ID may have changed, as it is under client control.
                // session ID is signed, so we can trust it
                return client.m_SessionId == sessionId;
            }
        );

        if (clientIt == m_Clients.end()) {
            return false; // client not registered with this vehicle, so ignore
        }

        clientIt->m_SessionId = sessionId;
        ESP_LOGI(TAG, "Refreshed session client: %s", sessionId.ToString().c_str());
        return true;
    }

    void PresenceTracker::ObserveClientVanished(Observer<UUID>& observer) {
        AddObserver(observer);
    }

    void PresenceTracker::RemoveObserver(Observer<UUID>& observer) {
        Observable<UUID>::RemoveObserver(observer);
    }

    void PresenceTracker::HandleAdvertisement(const lwdn::BleScanner::ServiceData& adv) {
        if (adv.m_Data.size() < ClientID{}.size() + sizeof(uint32_t)) {
            ESP_LOGW(TAG, "Advertisement is too short (%zu bytes)", adv.m_Data.size());
            return;
        }

        BitConverter<std::endian::big>::InputStream in(adv.m_Data.data());

        auto clientId = in.ReadBytesAs<ClientID>();
        auto totpValue = in.ReadUInt32();

        auto uptimeMs = SystemTime::UptimeMillis();
        auto unixMs = SystemTime::EpochMillis();

        {
            std::lock_guard lock(m_Mutex);
            RefreshClientPresent(clientId, totpValue, uptimeMs, unixMs);
        }
    }

    void PresenceTracker::RefreshClientPresent(const ClientID& clientId, uint32_t givenTotpValue, int64_t uptimeMs, int64_t unixMs) {
        auto clientIt = std::find_if(
            m_Clients.begin(),
            m_Clients.end(),
            [&clientId](const Client& client) {
                return client.m_ClientId == clientId;
            }
        );

        if (clientIt == m_Clients.end()) {
            return; // client not registered with this vehicle, so ignore
        }

        if (clientIt->m_LastSeenUnix / clientIt->m_TotpPeriodMs == unixMs / clientIt->m_TotpPeriodMs) {
            UpdateClientLastSeen(*clientIt, uptimeMs, unixMs);
            return; // seen in same slice, so do not recompute the TOTP needlessly
        }

        TOTP totp(clientIt->m_TotpSecret, MBEDTLS_MD_SHA256, 8, clientIt->m_TotpPeriodMs);
        bool totpOk = false;

        for (int i = -1; i <= 1; ++i) {
            int64_t totpTimeMs = unixMs + i * clientIt->m_TotpPeriodMs;
            uint32_t expectedTotpValue = totp.Generate(totpTimeMs);
            if (expectedTotpValue == givenTotpValue) {
                totpOk = true;
                break;
            }
        }

        if (!totpOk) {
            ESP_LOGW(TAG, "Received an invalid TOTP value !");
            return;
        }

        UpdateClientLastSeen(*clientIt, uptimeMs, unixMs);
    }

    void PresenceTracker::UpdateClientLastSeen(Client& client, int64_t uptimeMs, int64_t unixMs) {
        client.m_LastSeenUptime = uptimeMs;
        client.m_LastSeenUnix = unixMs;
        RecalculateNextWakeup();
    }

    void PresenceTracker::UpdateVanishedClients() {
        int64_t uptimeMs = SystemTime::UptimeMillis();

        std::erase_if(
            m_Clients,
            [this, uptimeMs](const Client& client) {
                if (IsVanished(client, uptimeMs)) {
                    // hack to process this inside erase_if (so that we only run one cycle)
                    ESP_LOGI(TAG, "Client session vanished: %s", client.m_SessionId.ToString().c_str());
                    NotifyObservers(&client.m_SessionId);
                    return true;
                }
                return false;
            }
        );

        RecalculateNextWakeup();
    }

    bool PresenceTracker::IsVanished(const Client& client, int64_t uptimeMs) const {
        return uptimeMs - client.m_LastSeenUptime >= m_TimeToVanishMs;
    }

    void PresenceTracker::RecalculateNextWakeup() {
        int64_t nextWakeupMs = INT64_MAX;

        for (const auto& client : m_Clients) {
            int64_t nextClientWakeup = client.m_LastSeenUptime + m_TimeToVanishMs;
            if (nextClientWakeup < nextWakeupMs) {
                nextWakeupMs = nextClientWakeup;
            }
        }

        if (nextWakeupMs != INT64_MAX) {
            m_NextWakeupTimer.Restart(nextWakeupMs * 1000);
        }
        else {
            m_NextWakeupTimer.Stop();
        }
    }
}