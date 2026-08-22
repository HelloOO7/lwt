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
#include "PSRAMAllocator.h"

namespace lwt {

    struct TicketValidationConfig {
        std::string TariffSystemID;
        int64_t PreauthorizationGracePeriodUs;
        int64_t ValidationProtectionPeriodUs;
    };

    class TicketPreauthRateLimiter {
    private:
        struct Entry {
            SHA256Hash TokenHash;
            int64_t TimestampUs;
        };

        size_t m_Capacity;
        int64_t m_MaxAgeUs;
        std::deque<Entry, psram_allocator<Entry>> m_Entries;

    public:
        TicketPreauthRateLimiter(size_t capacity, int64_t maxAgeUs);

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

        std::mutex m_DataMutex;
        bool m_HasData{ false };
        bool m_HasTVSData{ false };

        int32_t m_CurTripDelay{ 0 };
        int64_t m_TimeForTicketValidityStart{ 0 };
        std::string m_TariffZonesForValidation;
        std::vector<std::string> m_NextTariffZonesFromRoute;
        std::string m_NextTariffZonesForValidation;

        std::atomic<int> m_IsRazzia{ 0 };
        int64_t m_LastLocalRazziaOnTime{ 0 };

        flatbuffers::FlatBufferBuilder m_ValidationInfoFBB{ PSRAMFlatBufferBuilder() };

        std::mutex m_TokenGeneratorMutex;

    public:
        TicketValidationService(
            const TicketValidationConfig& config,
            PreauthorizationTokenManager& tokenManager, TicketSignatureVerifier& ticketVerifier,
            MOSClient& mosClient,
            TripInformationService& tripInfoService, vdv301::SubscriberTVS* tvsOpt = nullptr
        );
        virtual ~TicketValidationService() override;

        void Register(ServiceRegistry& registry);

        bool IsRazzia();

        virtual void OnChanged(const TripRouteInfo* result) override;
        virtual void OnChanged(const vdv301::SubscriberTVS::CurrentTariffStop* result) override;
        virtual void OnChanged(const vdv301::SubscriberTVS::RazziaState* result) override;

    private:
        void UpdateValidationInfo();
        void ResetValidationInfo();
        void FinishValidationInfo(flatbuffers::Offset<TicketValidationInfo> data);
        std::string GetCurrentTripKey();

        std::string GetTariffZonesOnlyMyTariffSystem(const std::string& tariffZones) const;

        static std::string ReduceNextTariffZones(const std::vector<std::string>& nextTariffZones, const std::string& curTariffZones);

        bool OpenActivationToken(const ActivationToken& token, uint16_t* pVersion = nullptr);
        void ReadActivationTokenSignature(const ActivationToken& activationToken, ByteSpan* pData, ByteSpan* pSignature, uint32_t* pKeyId);
        SHA256Hash HashActivationToken(const ByteSpan& token);
        ParsedActivationToken ParseActivationToken(const ByteSpan& tokenData);

        bool IsRazziaNoLock();
        bool SetRazziaBit(int bit, bool value);
        void ClearLocalRazziaIfExpired();
    };
}