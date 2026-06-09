package cz.spojenka.lwtp;

import java.util.Objects;

import javax.net.ssl.SSLContext;

import cz.spojenka.lwdn.LwdnAddress;

public class LwtpTLSConfig {

    private final LwdnAddress peerAddress;
    private final SSLContext sslContext;
    private final LwtpTLSPolicy tlsPolicy;

    private LwtpTLSConfig(LwdnAddress peerAddress, SSLContext sslContext, LwtpTLSPolicy tlsPolicy) {
        this.peerAddress = peerAddress;
        this.sslContext = sslContext;
        this.tlsPolicy = tlsPolicy;
    }

    public LwdnAddress getPeerAddress() {
        return peerAddress;
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public LwtpTLSPolicy getTlsPolicy() {
        return tlsPolicy;
    }

    public static class Builder {

        private final LwdnAddress peerAddress;
        private SSLContext sslContext;
        private LwtpTLSPolicy tlsPolicy;

        public Builder(LwdnAddress peerAddress) {
            this.peerAddress = peerAddress;
        }

        public Builder setSSLContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder setTLSPolicy(LwtpTLSPolicy tlsPolicy) {
            this.tlsPolicy = tlsPolicy;
            return this;
        }

        public LwtpTLSConfig build() {
            Objects.requireNonNull(peerAddress, "Peer address must not be null");
            Objects.requireNonNull(tlsPolicy, "TLS policy must not be null");
            if (tlsPolicy != LwtpTLSPolicy.UNSECURED && sslContext == null) {
                throw new IllegalStateException("SSLContext must be set for secured TLS policy");
            }
            return new LwtpTLSConfig(peerAddress, sslContext, tlsPolicy);
        }
    }
}
