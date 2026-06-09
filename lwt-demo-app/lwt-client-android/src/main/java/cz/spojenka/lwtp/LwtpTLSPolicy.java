package cz.spojenka.lwtp;

public enum LwtpTLSPolicy {
    /**
     * The client will not attempt to use TLS. If the server requires TLS, the connection will fail.
     */
    UNSECURED,
    /**
     * The client will attempt to use TLS if the server supports it.
     * If the server does not support TLS, the connection will proceed without encryption.
     * This mode may be vulnerable to MITM, as an attacker can pretend to be a server that does
     * not support TLS, causing the client to fall back to an unencrypted connection.
     */
    EXPLICIT_OPPORTUNISTIC,
    /**
     * The client will require the server to support TLS. It will issue a START_TLS command
     * and close the socket if the server does not allow it.
     */
    EXPLICIT_REQUIRED,
    /**
     * The client will begin all communication with a TLS handshake, similar to HTTPS.
     * It must be provided that the server expects this and performs the handshake immediately
     * after accepting the connection.
     */
    IMPLICIT
}
