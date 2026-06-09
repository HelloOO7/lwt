#pragma once

#include "mbedtls/x509_crt.h"
#include "mbedtls/pk.h"
#include "mbedtls/ssl.h"
#include "mbedtls/ctr_drbg.h"
#include "mbedtls/entropy.h"
#include "mbedtls/ssl_ticket.h"
#include <cstdint>

struct TlsEnvironment {
    mbedtls_x509_crt device_cert;
    mbedtls_pk_context device_key;
    mbedtls_entropy_context entropy;
    mbedtls_ctr_drbg_context ctr_drbg;
    mbedtls_ssl_ticket_context tickets;

    TlsEnvironment(const uint8_t* cert_start, const uint8_t* cert_end, const uint8_t* key_start, const uint8_t* key_end);
    ~TlsEnvironment();
};

void setup_tls_config(TlsEnvironment& env, mbedtls_ssl_config& ssl_config);