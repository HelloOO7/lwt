#include "tls_setup.h"

#include <cassert>
#include "esp_err.h"
#include "mbedtls/debug.h"
#include "esp_crt_bundle.h"

TlsEnvironment::TlsEnvironment(const uint8_t* cert_start, const uint8_t* cert_end, const uint8_t* key_start, const uint8_t* key_end) {
    mbedtls_x509_crt_init(&device_cert);
    mbedtls_pk_init(&device_key);
    mbedtls_ssl_ticket_init(&tickets);

    assert(mbedtls_x509_crt_parse(&device_cert, cert_start, cert_end - cert_start) == 0);
    assert(mbedtls_pk_parse_key(&device_key, key_start, key_end - key_start, nullptr, 0) == 0);
    assert(mbedtls_ssl_ticket_setup(&tickets, PSA_ALG_GCM, PSA_KEY_TYPE_AES, 128, 86400) == 0);
}

TlsEnvironment::~TlsEnvironment() {
    mbedtls_x509_crt_free(&device_cert);
    mbedtls_pk_free(&device_key);
    mbedtls_ssl_ticket_free(&tickets);
}

void setup_tls_config(TlsEnvironment& env, mbedtls_ssl_config& ssl_config) {
    mbedtls_ssl_config_init(&ssl_config);
    mbedtls_ssl_config_defaults(&ssl_config,
        MBEDTLS_SSL_IS_SERVER,
        MBEDTLS_SSL_TRANSPORT_STREAM,
        MBEDTLS_SSL_PRESET_DEFAULT);

    assert(mbedtls_ssl_conf_own_cert(&ssl_config, &env.device_cert, &env.device_key) == 0);
    mbedtls_ssl_conf_session_tickets_cb(&ssl_config, mbedtls_ssl_ticket_write, mbedtls_ssl_ticket_parse, &env.tickets);
    mbedtls_ssl_conf_authmode(&ssl_config, MBEDTLS_SSL_VERIFY_OPTIONAL);
    esp_crt_bundle_attach(&ssl_config);
}