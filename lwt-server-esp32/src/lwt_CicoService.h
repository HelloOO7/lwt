#pragma once

#include "lwt_ServiceRegistry.h"
#include "PSRAMTask.h"
#include "PSRAMContainers.h"
#include "lwt_MosClient.h"
#include "DigitalSignature.h"
#include "Certificate.h"
#include "lwt_TicketValidationService.h"
#include <mutex>
#include <condition_variable>
#include "lwdn_generated.h"
#include "cico_generated.h"
#include "esp_event.h"
#include "PubSubTask.h"
#include "lwt_PresenceTracker.h"

namespace lwt {

    struct CicoState {
        bool IsReady{ false };
    };

    class CheckOutList {
    private:
        struct CheckOutRecord {
            UUID SessionId;
            int64_t TimestampMs;
        };

        std::mutex m_Mutex;
        size_t m_Capacity;
        int64_t m_MaxAgeMs;
        psram_vector<CheckOutRecord> m_CheckedOutSessions;

    public:
        CheckOutList(size_t capacity, int64_t maxAgeMs);

        void AddCheckedOutSession(const UUID& sessionId, int64_t timestampMs);
        void RemoveIfPresent(const UUID& sessionId);
        bool Contains(const UUID& sessionId);

        void EnumerateCheckedOutSessions(std::function<void(const UUID& sessionId)> callback);

    private:
        decltype(m_CheckedOutSessions)::iterator Find(const UUID& sessionId);
        void EraseOldEntries(int64_t currentTimeMs);
    };

    class CicoService :
        Observer<TicketValidationState>,
        Observer<UUID>,
        public Observable<CicoState>,
        protected PubSubTask
    {
    private:
        struct ParsedRefreshToken {
            int64_t IssuedAt;
            uint32_t AccountId;
            UUID SessionId;
            UUID PreviousEventId;
        };

    private:
        TicketValidationConfig m_Config;

        PSRAMTask m_SyncTask;

        Certificate& m_TrustRoot;
        Certificate& m_DeviceCert;
        DigitalSignature& m_SigningKey;
        HMACSHA256& m_HMAC;
        TicketValidationService& m_TicketValidationService;

        MOSClient& m_MOSClient;
        psram_vector<MOSCICOEvent> m_EventBuffer;

        std::mutex m_SeedDerivationMutex;
        std::string m_LastSeedTripKey;
        ByteVector m_SeedDerivationSecret;

        CheckOutList m_CheckOutList;
        PresenceTracker& m_PresenceTracker;

        std::mutex m_StateMutex;
        bool m_CicoTimeReady{ false };
        bool m_CicoDataReady{ false };

        esp_event_handler_instance_t m_TimeSyncEventInstance{ nullptr };

    public:
        CicoService(
            const TicketValidationConfig& config,
            Certificate& trustRoot, Certificate& deviceCert, DigitalSignature& signingKey, HMACSHA256& hmac,
            TicketValidationService& ticketValidationService,
            MOSClient& mosClient,
            PresenceTracker& presenceTracker,
            int syncTaskPriority
        );
        ~CicoService();

        void Register(ServiceRegistry& registry);

        virtual void OnChanged(const TicketValidationState* result) override;
        virtual void OnChanged(const UUID* vanishedSession) override;

        void ObserveServiceState(Observer<CicoState>& observer);
        void RemoveObserver(Observer<CicoState>& observer);

        virtual void ProcessData() override;

    private:
        bool IsCicoReady();
        bool IsCicoReadyNoLock();
        void OnTimeSyncDone();
        void PublishServiceState();

        ByteVector CreateConfirmationToken(const MOSCheckInResponse& checkIn);
        bool VerifyConfirmationToken(const ByteSpan& token);
        bool ParseConfirmationToken(const ByteSpan& token, MOSCheckInResponse* pCheckIn, int64_t* pTimestamp);

        MOSCICOEvent EventStartCico(const MOSCheckInResponse& checkIn, const psram_string& metadata);
        MOSCICOEvent EventRefreshCico(const ParsedRefreshToken& refreshToken, const psram_string& metadata, MOSCICOEventType type);
        void EnqueuePushEvent(MOSCICOEvent&& event);

        ByteVector GenerateETD(int64_t validFromEMs, int64_t validToEMs, const UUID& sessionId, const psram_string& metadata);
        void SignETD(ByteVector& etd, ByteVector* pSignature);

        flatbuffers::Offset<flatbuffers::Vector<uint8_t>> GetSeedDerivationSecretToFlatbuffer(flatbuffers::FlatBufferBuilder& builder);
        void UpdateSeedDerivationSecret();
        SHA256Hash DeriveTotpSeed(const ByteSpan& ticketSignature);

        flatbuffers::Offset<flatbuffers::Vector<flatbuffers::Offset<LwdnAddress>>> GetDeviceAddressesToFlatbuffer(flatbuffers::FlatBufferBuilder& builder);

        ByteVector CreateRefreshToken(int64_t issuedAt, const MOSCICOEvent& prevEvent);
        int VerifyAndParseRefreshToken(const CICOFragmentRefreshRequest& request, ParsedRefreshToken* pParsedToken, bool* pSameIssuer);
        bool ParseRefreshToken(const ByteSpan& token, ParsedRefreshToken* pParsedToken);
        bool IsIssuerTrusted(Certificate& issuerCert);
        static bool IsRefreshSelfCertificate(const CICOFragmentRefreshRequest& request);

        void ProcessCicoRequest(MOSCICOEvent&& event, flatbuffers::FlatBufferBuilder& responseFbb);

        void SyncEventsLoop();
        bool SendEventsToServer();
        static void SyncEventsTaskFunc(void* arg);
    };
}