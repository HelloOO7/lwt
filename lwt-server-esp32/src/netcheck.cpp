#include "netcheck.h"
#include "esp_netif.h"

// written by ChatGPT

static bool has_network_configuration_impl()
{
    esp_netif_t* netif = nullptr;

    while ((netif = esp_netif_next_unsafe(netif)) != nullptr) {
        if (!esp_netif_is_netif_up(netif))
            continue;

        // IPv4
        esp_netif_ip_info_t ip4{};
        if (esp_netif_get_ip_info(netif, &ip4) == ESP_OK &&
            ip4.ip.addr != 0) {
            return true;
        }

#if CONFIG_LWIP_IPV6
        // IPv6: global address
        esp_ip6_addr_t ip6{};
        if (esp_netif_get_ip6_global(netif, &ip6) == ESP_OK) {
            return true;
    }
#endif
}

    return false;
}

static esp_err_t check_network_callback(void* ctx)
{
    *static_cast<bool*>(ctx) = has_network_configuration_impl();
    return ESP_OK;
}

bool netcheck_is_connected()
{
    bool result = false;

    if (esp_netif_tcpip_exec(check_network_callback, &result) != ESP_OK)
        return false;

    return result;
}