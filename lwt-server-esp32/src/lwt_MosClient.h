#pragma once

#include <string>
#include <cstdint>
#include "CommonTypes.h"
#include "esp_http_client.h"
#include "PSRAMContainers.h"
#include "ISO8601.h"
#include <optional>
#include <variant>

namespace lwt {

    struct MOSTicketActivationParams {
        std::optional<std::variant<OffsetDateTime, LocalDateTime>> Time;
        bool ActivateNowIfEarlier{ false };
        bool ClientIntegrityAttested{ false };
        std::string Zones;
        std::string ClientAppID;
        std::string LwtMetadata;
    };

    struct MOSTicketPayload {
        psram_vector<uint8_t> ETD;
        psram_vector<uint8_t> TOTPSeed;
    };

    struct MOSTicket {
        uint64_t TicketId;

        MOSTicketPayload Payload;

        OffsetDateTime ValidSince;
        OffsetDateTime ValidUntil;
        std::string ValidZones;

        std::string ActivatedAppID;
        OffsetDateTime ActivationTime;
    };

    enum class MOSCICOEventType {
        CHECK_IN,
        CHECK_OUT,
        REFRESH,
    };

    struct MOSCICOEvent {
        UUID EventId;
        UUID PreviousEventId;
        UUID SessionId;
        uint32_t AccountId;

        int64_t LocalTimestamp;
        
        MOSCICOEventType EventType;
        psram_string LwtMetadata;
    };

    struct MOSCICOEventBatch {
        psram_vector<MOSCICOEvent> Events;
        int64_t CurrentLocalTimestamp;
    };

    class MOSClient {
    private:
        std::string m_BaseUrl;
        esp_http_client_config_t m_HttpClientConfig{};

    public:
        MOSClient(const std::string& baseUrl, const ByteSpan& tlsClientCertChain, const ByteSpan& tlsClientPrivateKey);

        int ActivateTicket(uint64_t ticketId, const MOSTicketActivationParams& params, MOSTicket* pActivatedTicket);
        int PushCICOEvents(const MOSCICOEventBatch& eventBatch);

    private:
        /**
         * @brief Call a HTTP endpoint.
         *
         * @param method Method
         * @param path Path, must start with '/'
         * @param requestBody Request body, assumed JSON
         * @param pResponseBody Pointer to a string to receive the response body
         * @return int -1: Failed to initialize HTTP client
         *             -2: HTTP request failed
         *             >=0: HTTP status code.
         */
        int PerformHttpRequest(esp_http_client_method_t method, const std::string& path, const psram_string& requestBody, psram_string* pResponseBody);
    };
}