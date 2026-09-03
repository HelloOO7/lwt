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

namespace lwt {

    class CicoService {
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

        std::mutex m_EventsMutex;
        MOSClient& m_MOSClient;
        psram_vector<MOSCICOEvent> m_EventBuffer;
        std::condition_variable m_HasEventsCV;

        bool m_RequestClose{ false };
        std::condition_variable m_Closed;

        std::mutex m_SeedDerivationMutex;
        std::string m_LastSeedTripKey;
        ByteVector m_SeedDerivationSecret;

    public:
        CicoService(
            const TicketValidationConfig& config,
            Certificate& trustRoot, Certificate& deviceCert, DigitalSignature& signingKey, HMACSHA256& hmac,
            TicketValidationService& ticketValidationService,
            MOSClient& mosClient,
            int syncTaskPriority
        );
        ~CicoService();

        void Register(ServiceRegistry& registry);

    private:
        bool IsCicoReady();

        ByteVector CreateConfirmationToken(const MOSCheckInResponse& checkIn);
        bool VerifyConfirmationToken(const ByteSpan& token);
        bool ParseConfirmationToken(const ByteSpan& token, MOSCheckInResponse* pCheckIn, int64_t* pTimestamp);

        MOSCICOEvent EventStartCico(const MOSCheckInResponse& checkIn, const psram_string& metadata);
        MOSCICOEvent EventRefreshCico(const ParsedRefreshToken& refreshToken, const psram_string& metadata, MOSCICOEventType type);
        void EnqueuePushEvent(MOSCICOEvent&& event);

        ByteVector GenerateETD(int64_t validFromEMs, int64_t validToEMs, const UUID& sessionId, const psram_string& metadata);
        void SignETD(const ByteVector& etd, ByteVector* pSignature);

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