#pragma once

#include <string>
#include <cstdint>
#include "CommonTypes.h"
#include "UUID.h"
#include "esp_http_client.h"
#include "PSRAMContainers.h"
#include "ISO8601.h"
#include "Certificate.h"
#include <optional>
#include <variant>
#include "nlohmann/json.hpp"

namespace lwt {

    // the actual object hierarchy is light enough to store in RAM. we want only strings to be in PSRAM.
    using psram_json = nlohmann::basic_json<std::map, std::vector, psram_string>;

    struct MOSTicketActivationParams {
        std::optional<std::variant<OffsetDateTime, LocalDateTime>> Time;
        bool ActivateNowIfEarlier{ false };
        bool ClientIntegrityAttested{ false };
        std::string Zones;
        std::string ClientAppID;
        psram_string LwtMetadata;
    };

    struct MOSTicketPayload {
        ByteVector ETD;
        ByteVector TOTPSeed;
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
        OffsetDateTime AbsoluteTimestamp;

        MOSCICOEventType EventType;
        psram_string LwtMetadata;
    };

    struct MOSCICOEventBatch {
        psram_vector<MOSCICOEvent> Events;
        int64_t CurrentLocalTimestamp;
    };

    struct MOSCheckInRequest {
        ByteVector CheckInToken;
    };

    struct MOSCheckInResponse {
        uint32_t AccountId;
        UUID SessionId;
    };

    class MOSClient {
    private:
        std::string m_BaseUrl;
        esp_http_client_config_t m_HttpClientConfig{};

    public:
        MOSClient(const std::string& baseUrl, const Certificate& tlsClientCertChain, const ByteSpan& tlsClientPrivateKey);

        int ActivateTicket(uint64_t ticketId, const MOSTicketActivationParams& params, MOSTicket* pActivatedTicket);

        int CICOPushEvents(const MOSCICOEventBatch& eventBatch);
        int CICOCheckIn(const MOSCheckInRequest& request, MOSCheckInResponse* pResponse);

        static bool IsStatusOK(int statusCode);

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
        int PerformJsonHttpRequest(esp_http_client_method_t method, const std::string& path, const psram_json& requestJson, psram_json* pResponseJson);
    };
}