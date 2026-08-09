#pragma once

void wifi_init_default();
void wifi_init_sta(const char* ssid, const char* password, int retry_max);
void wifi_init_nan();