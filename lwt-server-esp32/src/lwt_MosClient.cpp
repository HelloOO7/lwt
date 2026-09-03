#include "lwt_MosClient.h"

#include "esp_log.h"
#include "esp_crt_bundle.h"

#include "mbedtls/base64.h"

#include <iostream>

#include "Overloaded.h"

namespace lwt {

    static constexpr const char* TAG = "MOSClient";

    esp_err_t client_event_data_handler(esp_http_client_event_handle_t evt);

    MOSClient::MOSClient(const std::string& baseUrl, const Certificate& tlsClientCertChain, const ByteSpan& tlsClientPrivateKey) :
        m_BaseUrl{ baseUrl }
    {
        auto clientCertDer = tlsClientCertChain.GetCertificateDer();

        m_HttpClientConfig.url = m_BaseUrl.c_str();
        m_HttpClientConfig.client_cert_der = (const char*)clientCertDer.data();
        m_HttpClientConfig.client_cert_len = clientCertDer.size();
        m_HttpClientConfig.client_key_pem = (const char*)tlsClientPrivateKey.data();
        m_HttpClientConfig.client_key_len = tlsClientPrivateKey.size();
        m_HttpClientConfig.use_global_ca_store = true;
        m_HttpClientConfig.crt_bundle_attach = esp_crt_bundle_attach;
        m_HttpClientConfig.user_agent = "LWT/1.0";
        m_HttpClientConfig.timeout_ms = 5000;
        m_HttpClientConfig.event_handler = client_event_data_handler;
    }

    OffsetDateTime DateTimeFromJson(const psram_json& json) {
        return OffsetDateTime::parse(json.get_ref<const psram_string&>().c_str());
    }

    ByteVector JsonToByteVector(const psram_json& json) {
        auto str = json.get_ref<const psram_string&>();
        // base64 decode
        size_t decodedLen = 0;
        mbedtls_base64_decode(nullptr, 0, &decodedLen, (const unsigned char*)str.data(), str.size());
        ByteVector decoded(decodedLen);
        mbedtls_base64_decode(decoded.data(), decoded.size(), &decodedLen, (const unsigned char*)str.data(), str.size());
        return decoded;
    }

    psram_json ByteVectorToJson(const ByteVector& vec) {
        size_t encodedLen = 0;
        mbedtls_base64_encode(nullptr, 0, &encodedLen, vec.data(), vec.size());
        psram_string encoded(encodedLen, '\0');
        mbedtls_base64_encode((unsigned char*)encoded.data(), encoded.size(), &encodedLen, vec.data(), vec.size());
        return psram_json(encoded);
    }

    int MOSClient::ActivateTicket(uint64_t ticketId, const MOSTicketActivationParams& params, MOSTicket* pActivatedTicket) {
        psram_json request{
            {"activateNowIfEarlier", params.ActivateNowIfEarlier},
            {"clientIntegrityAttested", params.ClientIntegrityAttested},
            {"zones", params.Zones},
            {"appId", params.ClientAppID},
            {"activationSourceMetadata", params.LwtMetadata}
        };
        if (params.Time) {
            std::visit(
                overloaded{
                    [&](const OffsetDateTime& odt) { request["time"] = odt.to_string(); },
                    [&](const LocalDateTime& ldt) { request["time"] = ldt.to_string(); }
                },
                *params.Time
            );
        }

        psram_json& response = request; // reuse

        int status = PerformJsonHttpRequest(HTTP_METHOD_POST, "/tickets/" + std::to_string(ticketId) + "/activate", request, &response); //reuse request memory for response
        if (!IsStatusOK(status)) {
            return status;
        }

        response.at("id").get_to(pActivatedTicket->TicketId);
        pActivatedTicket->Payload.ETD = JsonToByteVector(response.at("payload").at("etd"));
        pActivatedTicket->Payload.TOTPSeed = JsonToByteVector(response.at("payload").at("derivedTotpSeed"));
        pActivatedTicket->ValidSince = DateTimeFromJson(response.at("validSince"));
        pActivatedTicket->ValidUntil = DateTimeFromJson(response.at("validUntil"));
        response.at("validZones").get_to(pActivatedTicket->ValidZones);
        response.at("activationRecord").at("appId").get_to(pActivatedTicket->ActivatedAppID);
        pActivatedTicket->ActivationTime = DateTimeFromJson(response.at("activationRecord").at("activationTime"));

        return status;
    }

    int MOSClient::CICOCheckIn(const MOSCheckInRequest& request, MOSCheckInResponse* pResponse) {
        psram_json requestJson{
            {"checkInToken", ByteVectorToJson(request.CheckInToken)}
        };
        psram_json& responseJson = requestJson; // reuse

        int status = PerformJsonHttpRequest(HTTP_METHOD_POST, "/cico/check-in", requestJson, &responseJson);
        if (!IsStatusOK(status)) {
            return status;
        }

        responseJson.at("accountId").get_to(pResponse->AccountId);
        pResponse->SessionId = UUID::Parse(responseJson.at("sessionId").get_ref<const psram_string&>());

        return status;
    }

    const char* CICOEventTypeToString(MOSCICOEventType type) {
        switch (type) {
        case MOSCICOEventType::CHECK_IN: return "CHECK_IN";
        case MOSCICOEventType::CHECK_OUT: return "CHECK_OUT";
        case MOSCICOEventType::REFRESH: return "REFRESH";
        default: return "UNKNOWN";
        }
    }

    int MOSClient::CICOPushEvents(const MOSCICOEventBatch& eventBatch) {
        psram_json requestJson = psram_json::array();
        for (const auto& event : eventBatch.Events) {
            psram_json eventJson{
                {"eventId", event.EventId.ToString()},
                {"previousEventId", event.PreviousEventId.ToString()},
                {"sessionId", event.SessionId.ToString()},
                {"accountId", event.AccountId},
                {"localTimestamp", event.LocalTimestamp},
                {"absoluteTimestamp", event.AbsoluteTimestamp.to_string()},
                {"eventType", CICOEventTypeToString(event.EventType)},
                {"lwtMetadata", event.LwtMetadata}
            };
            requestJson.push_back(std::move(eventJson));
        }

        psram_json& responseJson = requestJson; // reuse

        int status = PerformJsonHttpRequest(HTTP_METHOD_POST, "/cico/events", requestJson, &responseJson);

        // no response for now
        (void)responseJson;
        
        return status;
    }

    bool MOSClient::IsStatusOK(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    struct ClientEventData {
        psram_string* m_ResponseBody;
        bool m_ResponseBodyInitialized{ false };
    };

    esp_err_t client_event_data_handler(esp_http_client_event_handle_t evt) {
        if (evt->event_id == HTTP_EVENT_ON_DATA) {
            ClientEventData* pEventData = static_cast<ClientEventData*>(evt->user_data);
            if (pEventData && pEventData->m_ResponseBody) {
                if (!pEventData->m_ResponseBodyInitialized) {
                    pEventData->m_ResponseBodyInitialized = true;
                    pEventData->m_ResponseBody->assign(static_cast<const char*>(evt->data), evt->data_len);
                }
                else {
                    pEventData->m_ResponseBody->append(static_cast<const char*>(evt->data), evt->data_len);
                }
            }
        }
        return ESP_OK;
    }

    int MOSClient::PerformHttpRequest(esp_http_client_method_t method, const std::string& path, const psram_string& requestBody, psram_string* pResponseBody) {
        esp_http_client_handle_t client = esp_http_client_init(&m_HttpClientConfig);
        if (!client) {
            ESP_LOGE(TAG, "Failed to initialize HTTP client");
            return -1; // Failed to initialize HTTP client
        }

        std::string url = m_BaseUrl + path;

        ClientEventData eventData{ pResponseBody, false };

        esp_http_client_set_method(client, method);
        esp_http_client_set_url(client, url.c_str());
        esp_http_client_set_header(client, "Accept", "application/json");
        esp_http_client_set_header(client, "Content-Type", "application/json");
        esp_http_client_set_post_field(client, requestBody.c_str(), requestBody.size());
        esp_http_client_set_user_data(client, &eventData);

        esp_err_t err = esp_http_client_perform(client);
        if (err != ESP_OK) {
            ESP_LOGE(TAG, "HTTP request failed");
            esp_http_client_cleanup(client);
            return -2; // HTTP request failed
        }

        int status = esp_http_client_get_status_code(client);

        esp_http_client_cleanup(client);

        return status;
    }

    const char* MethodToString(esp_http_client_method_t method) {
        switch (method) {
        case HTTP_METHOD_GET: return "GET";
        case HTTP_METHOD_POST: return "POST";
        case HTTP_METHOD_PUT: return "PUT";
        case HTTP_METHOD_PATCH: return "PATCH";
        case HTTP_METHOD_DELETE: return "DELETE";
        default: return "UNKNOWN";
        }
    }

    int MOSClient::PerformJsonHttpRequest(esp_http_client_method_t method, const std::string& path, const psram_json& requestJson, psram_json* pResponseJson) {
        psram_string requestBody = requestJson.dump();
        std::cout << "--- " << MethodToString(method) << " " << path << " --->" << std::endl;
        if (requestBody.length() < 512) {
            std::cout << std::setw(4) << requestJson << std::endl;
        }
        else {
            std::cout << "Request body too large to display (" << requestBody.length() << " bytes)" << std::endl;
        }
        psram_string responseBody;
        int status = PerformHttpRequest(method, path, requestBody, &responseBody);
        std::cout << "<--- " << status << " " << path << " ---" << std::endl;
        if (IsStatusOK(status)) {
            if (pResponseJson) {
                *pResponseJson = psram_json::parse(responseBody);
            }
        }
        else {
            if (status > 0) {
                std::cout << responseBody << std::endl;
            }
        }
        return status;
    }
}