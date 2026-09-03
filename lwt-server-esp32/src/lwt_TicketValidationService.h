#pragma once

#include "vdv_SubscriberTVS.h"
#include "flatbuffer_util.h"
#include "lwt_TripInformationService.h"
#include "ticket_validation_generated.h"
#include <string>
#include <deque>
#include <atomic>
#include "lwt_ServiceRegistry.h"
#include "lwt_PreauthorizationTokenManager.h"
#include "lwt_TicketSignatureVerifier.h"
#include "lwt_MosClient.h"
#include "vdv_PublisherRCS.h"
#include "PSRAMAllocator.h"
#include "esp_timer.h"
#include "TimerProc.h"

namespace lwt {

    struct TicketValidationConfig {
        std::string TariffSystemID;
        std::string TicketIssuerID;
        int64_t PreauthorizationGracePeriodMs;
        int64_t ValidationProtectionPeriodMs;
        int64_t CicoConfirmationTokenExpiryMs;
        int64_t CicoTicketTtlMs;
    };

    class TicketPreauthRateLimiter {
    private:
        struct Entry {
            SHA256Hash TokenHash;
            int64_t TimestampMs;
        };

        size_t m_Capacity;
        int64_t m_MaxAgeMs;
        std::deque<Entry, psram_allocator<Entry>> m_Entries;

    public:
        TicketPreauthRateLimiter(size_t capacity, int64_t maxAgeMs);

        void InvalidateAll();

        /**
         * @brief Check if a token hash is not rate limited, i. e. it has not yet been used or enough time has passed since its
         * last use to exceed the configured maximum age.
         *
         * @param tokenHash The token hash
         * @param currentTime The current system time to use as a point of reference for measuring age
         * @param pBlockingTokenTimestamp If set, the variable will receive the timestamp from which the age of the token hash that blocks
         * more tokens from being issued is measured.
         * @return true
         * @return false
         */
        bool IsTokenHashAllowed(const SHA256HashView& tokenHash, int64_t currentTime, int64_t* pBlockingTokenTimestamp = nullptr);
        void RegisterTokenHashUsed(const SHA256HashView& tokenHash, int64_t currentTime);

    private:
        void InvalidateOldEntries(int64_t currentTime);
    };

    class TicketValidationService : Observer<TripRouteInfo>, Observer<vdv301::SubscriberTVS::CurrentTariffStop>, Observer<vdv301::SubscriberTVS::RazziaState>
    {
    private:
        static constexpr int64_t RAZZIA_HEARTBEAT_MAX_SECONDS = 30;

        struct ParsedActivationToken {
            static constexpr uint32_t FLAG_DISALLOW_PREAUTH = (1 << 0);

            uint64_t TicketId;
            uint32_t Flags;
        };

        static constexpr int RAZZIA_TVS_BIT = (1 << 0);
        static constexpr int RAZZIA_LOCAL_BIT = (1 << 1);

    private:
        TicketValidationConfig m_Config;
        PreauthorizationTokenManager& m_TokenManager;
        TicketPreauthRateLimiter m_PreauthRateLimiter;
        TicketSignatureVerifier& m_TicketVerifier;
        MOSClient& m_MOSClient;
        TripInformationService& m_TripInfoService;
        vdv301::SubscriberTVS* m_TVS;
        vdv301::PublisherRCS* m_RCS;

        std::mutex m_DataMutex;
        bool m_HasData{ false };
        bool m_HasTVSData{ false };

        int32_t m_CurTripDelay{ 0 };
        int64_t m_TimeForTicketValidityStart{ 0 };
        std::string m_TariffZonesForValidation;
        std::vector<std::string> m_NextTariffZonesFromRoute;
        std::string m_NextTariffZonesForValidation;
        psram_string m_CurrentValidationMetadata;

        std::atomic<int> m_IsRazzia{ 0 };
        int64_t m_LastLocalRazziaOnTime{ 0 };
        TimerProc m_RazziaOffTimer;

        flatbuffers::FlatBufferBuilder m_ValidationInfoFBB{ PSRAMFlatBufferBuilder() };

        std::mutex m_TokenGeneratorMutex;

    public:
        TicketValidationService(
            const TicketValidationConfig& config,
            PreauthorizationTokenManager& tokenManager, TicketSignatureVerifier& ticketVerifier,
            MOSClient& mosClient,
            TripInformationService& tripInfoService, vdv301::SubscriberTVS* tvsOpt = nullptr,
            vdv301::PublisherRCS* rcsOpt = nullptr
        );
        virtual ~TicketValidationService() override;

        void Register(ServiceRegistry& registry);

        bool IsRazzia();
        psram_string GetCurrentValidationMetadata();
        std::string GetCurrentTripKey();

        virtual void OnChanged(const TripRouteInfo* result) override;
        virtual void OnChanged(const vdv301::SubscriberTVS::CurrentTariffStop* result) override;
        virtual void OnChanged(const vdv301::SubscriberTVS::RazziaState* result) override;

    private:
        void UpdateValidationInfo();
        void ResetValidationInfo();
        void FinishValidationInfo(flatbuffers::Offset<TicketValidationInfo> data);
        const TicketValidationInfo* GetValidationInfo() const;
        std::string GetCurrentTripKeyNoLock() const;

        std::string GetTariffZonesOnlyMyTariffSystem(const std::string& tariffZones) const;

        static std::string ReduceNextTariffZones(const std::vector<std::string>& nextTariffZones, const std::string& curTariffZones);

        int OpenActivationToken(const ActivationToken& token, SHA256Hash* pHash, ParsedActivationToken* pParsedToken);
        SHA256Hash HashActivationToken(const ByteSpan& token);
        ParsedActivationToken ParseActivationToken(const ByteSpan& tokenData);

        bool IsRazziaNoLock();
        bool SetRazziaBit(int bit, bool value);
        bool SetLocalRazziaState(bool isRazzia);
        void ClearLocalRazziaIfExpired();
        void PropagateRazziaStateToRCS(bool isRazzia);
    };
}