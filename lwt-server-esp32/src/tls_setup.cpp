#include "tls_setup.h"

#include <cassert>
#include "esp_err.h"
#include "mbedtls/debug.h"

TlsEnvironment::TlsEnvironment(const uint8_t* cert_start, const uint8_t* cert_end, const uint8_t* key_start, const uint8_t* key_end) {
    mbedtls_x509_crt_init(&device_cert);
    mbedtls_pk_init(&device_key);
    mbedtls_entropy_init(&entropy);
    mbedtls_ctr_drbg_init(&ctr_drbg);
    mbedtls_ssl_ticket_init(&tickets);

    assert(mbedtls_ctr_drbg_seed(&ctr_drbg, mbedtls_entropy_func, &entropy, nullptr, 0) == 0);
    assert(mbedtls_x509_crt_parse(&device_cert, cert_start, cert_end - cert_start) == 0);
    assert(mbedtls_pk_parse_key(&device_key, key_start, key_end - key_start, nullptr, 0, mbedtls_ctr_drbg_random, &ctr_drbg) == 0);
    assert(mbedtls_ssl_ticket_setup(&tickets, mbedtls_ctr_drbg_random, &ctr_drbg, MBEDTLS_CIPHER_AES_128_GCM, 86400) == 0);
}

TlsEnvironment::~TlsEnvironment() {
    mbedtls_x509_crt_free(&device_cert);
    mbedtls_pk_free(&device_key);
    mbedtls_ctr_drbg_free(&ctr_drbg);
    mbedtls_entropy_free(&entropy);
    mbedtls_ssl_ticket_free(&tickets);
}

static mbedtls_entropy_context entropy;

void setup_tls_config(TlsEnvironment& env, mbedtls_ssl_config& ssl_config) {
    mbedtls_ssl_config_init(&ssl_config);
    mbedtls_ssl_config_defaults(&ssl_config,
        MBEDTLS_SSL_IS_SERVER,
        MBEDTLS_SSL_TRANSPORT_STREAM,
        MBEDTLS_SSL_PRESET_DEFAULT);

    mbedtls_ssl_conf_rng(&ssl_config, mbedtls_ctr_drbg_random, &env.ctr_drbg);
    assert(mbedtls_ssl_conf_own_cert(&ssl_config, &env.device_cert, &env.device_key) == 0);
    mbedtls_ssl_conf_session_tickets_cb(&ssl_config, mbedtls_ssl_ticket_write, mbedtls_ssl_ticket_parse, &env.tickets);
}