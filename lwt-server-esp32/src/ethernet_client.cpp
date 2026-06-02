#include "ethernet_client.h"

#include "ethernet_init.h"
#include "esp_netif.h"
#include "esp_eth_netif_glue.h"
#include <cstring>

esp_eth_handle_t* eth_handles;
uint8_t eth_cnt;

void ethernet_init(void) {
    ESP_ERROR_CHECK(ethernet_init_all(&eth_handles, &eth_cnt));
}

// https://github.com/espressif/esp-idf/blob/release/v5.5/examples/ethernet/basic/main/ethernet_example_main.c

void ethernet_init_netif(void) {
    // Create instance(s) of esp-netif for Ethernet(s)
    if (eth_cnt == 1) {
        // Use ESP_NETIF_DEFAULT_ETH when just one Ethernet interface is used and you don't need to modify
        // default esp-netif configuration parameters.
        esp_netif_config_t cfg = ESP_NETIF_DEFAULT_ETH();
        esp_netif_t* netif = esp_netif_new(&cfg);
        esp_eth_netif_glue_t* eth_netif_glue = esp_eth_new_netif_glue(eth_handles[0]);
        // Attach Ethernet driver to TCP/IP stack
        ESP_ERROR_CHECK(esp_netif_attach(netif, eth_netif_glue));
    }
    else {
        // Use ESP_NETIF_INHERENT_DEFAULT_ETH when multiple Ethernet interfaces are used and so you need to modify
        // esp-netif configuration parameters for each interface (name, priority, etc.).
        esp_netif_inherent_config_t esp_netif_config = ESP_NETIF_INHERENT_DEFAULT_ETH();
        esp_netif_config_t cfg_spi = {
            .base = &esp_netif_config,
            .stack = ESP_NETIF_NETSTACK_DEFAULT_ETH
        };
        char if_key_str[10];
        char if_desc_str[10];
        char num_str[3];
        for (int i = 0; i < eth_cnt; i++) {
            itoa(i, num_str, 10);
            strcat(strcpy(if_key_str, "ETH_"), num_str);
            strcat(strcpy(if_desc_str, "eth"), num_str);
            esp_netif_config.if_key = if_key_str;
            esp_netif_config.if_desc = if_desc_str;
            esp_netif_config.route_prio -= i * 5;
            esp_netif_t* netif = esp_netif_new(&cfg_spi);
            esp_eth_netif_glue_t* eth_netif_glue = esp_eth_new_netif_glue(eth_handles[i]);
            // Attach Ethernet driver to TCP/IP stack
            ESP_ERROR_CHECK(esp_netif_attach(netif, eth_netif_glue));
        }
    }

    for (int i = 0; i < eth_cnt; i++) {
        ESP_ERROR_CHECK(esp_eth_start(eth_handles[i]));
    }
}