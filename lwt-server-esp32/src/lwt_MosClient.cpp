#include "lwt_MosClient.h"

#include "esp_log.h"
#include "esp_crt_bundle.h"

#include "nlohmann/json.hpp"

#include <iostream>

#include "Overloaded.h"

namespace lwt {

    // the actual object hierarchy is light enough to store in RAM. we want only strings to be in PSRAM.
    using psram_json = nlohmann::basic_json<std::map, std::vector, psram_string>;

    static constexpr const char* TAG = "MOSClient";

    esp_err_t client_event_data_handler(esp_http_client_event_handle_t evt);

    MOSClient::MOSClient(const std::string& baseUrl, const ByteSpan& tlsClientCertChain, const ByteSpan& tlsClientPrivateKey) :
        m_BaseUrl{ baseUrl }
    {
        m_HttpClientConfig.url = m_BaseUrl.c_str();
        m_HttpClientConfig.cert_pem = (const char*)tlsClientCertChain.data();
        m_HttpClientConfig.cert_len = tlsClientCertChain.size();
        m_HttpClientConfig.client_cert_pem = (const char*)tlsClientCertChain.data();
        m_HttpClientConfig.client_cert_len = tlsClientCertChain.size();
        m_HttpClientConfig.client_key_pem = (const char*)tlsClientPrivateKey.data();
        m_HttpClientConfig.client_key_len = tlsClientPrivateKey.size();
        m_HttpClientConfig.use_global_ca_store = true;
        m_HttpClientConfig.crt_bundle_attach = esp_crt_bundle_attach;
        m_HttpClientConfig.user_agent = "LWT/1.0";
        m_HttpClientConfig.timeout_ms = 5000;
        m_HttpClientConfig.event_handler = client_event_data_handler;
    }

    OffsetDateTime DateTimeFromJson(const psram_json& json) {
        return OffsetDateTime::parse(json.get<std::string>());
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

        std::cout << "--- POST MOS::ActivateTicket --->" << std::endl;
        std::cout << std::setw(4) << request << std::endl;

        psram_string body = request.dump();

        int status = PerformHttpRequest(HTTP_METHOD_POST, "/tickets/" + std::to_string(ticketId) + "/activate", body, &body); //reuse request memory for response

        std::cout << "<--- MOS::ActivateTicket ---" << std::endl;

        if (status != 200) {
            std::cout << body << std::endl;
            return status;
        }

        psram_json response = psram_json::parse(body);

        std::cout << std::setw(4) << response << std::endl;

        response.at("id").get_to(pActivatedTicket->TicketId);
        response.at("payload").at("etd").get_to(pActivatedTicket->Payload.ETD);
        response.at("payload").at("derivedTotpSeed").get_to(pActivatedTicket->Payload.TOTPSeed);
        pActivatedTicket->ValidSince = DateTimeFromJson(response.at("validSince"));
        pActivatedTicket->ValidUntil = DateTimeFromJson(response.at("validUntil"));
        response.at("validZones").get_to(pActivatedTicket->ValidZones);
        response.at("activationRecord").at("appId").get_to(pActivatedTicket->ActivatedAppID);
        pActivatedTicket->ActivationTime = DateTimeFromJson(response.at("activationRecord").at("activationTime"));

        return status;
    }

    struct ClientEventData {
        psram_string* m_ResponseBody;
        bool m_ResponseBodyInitialized { false };
    };

    esp_err_t client_event_data_handler(esp_http_client_event_handle_t evt) {
        if (evt->event_id == HTTP_EVENT_ON_DATA) {
            ClientEventData* pEventData = static_cast<ClientEventData*>(evt->user_data);
            if (pEventData && pEventData->m_ResponseBody) {
                if (!pEventData->m_ResponseBodyInitialized) {
                    pEventData->m_ResponseBodyInitialized = true;
                    pEventData->m_ResponseBody->assign(static_cast<const char*>(evt->data), evt->data_len);
                } else {
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

        ClientEventData eventData { pResponseBody, false };

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

        esp_http_client_cleanup(client);

        return esp_http_client_get_status_code(client);
    }
}