package cz.spojenka.lwt.ticketingserver.services;

import cz.dpp.praguepublictransport.etd.ETDUtils;
import cz.dpp.praguepublictransport.etd.LitackaETD;
import jakarta.annotation.PostConstruct;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import shaded.org.apache.commons.codec.digest.DigestUtils;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Objects;

@Service
public class TicketSigningService {

    @Value("${tickets.signing-key-path.priv}")
    private String privSigningKeyPath;

    private PrivateKey signingKey;

    public TicketSigningService() {
    }

    @PostConstruct
    private void loadSigningKey() throws GeneralSecurityException, IOException {
        Objects.requireNonNull(privSigningKeyPath, "Private signing key path must be set");
        signingKey = loadSigningKeyFromPath(privSigningKeyPath);
    }

    private static PrivateKey loadSigningKeyFromPath(String path) throws IOException, GeneralSecurityException {
        try (PEMParser parser = new PEMParser(new FileReader(path))) {
            Object object = parser.readObject();

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (object instanceof PEMKeyPair keyPair) {
                return converter.getKeyPair(keyPair).getPrivate();
            }

            if (object instanceof PrivateKeyInfo privateKeyInfo) {
                return converter.getPrivateKey(privateKeyInfo);
            }

            throw new IllegalArgumentException("Unsupported PEM object: " + object.getClass());
        }
    }

    private byte[] sign(byte[] data, String hash) {
        try {
            Signature signature = Signature.getInstance(hash + "with" + signingKey.getAlgorithm());
            signature.initSign(signingKey);
            signature.update(data);
            return signature.sign();
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] signString(byte[] data) {
        return sign(data, "SHA256");
    }

    private byte[] signHash(byte[] hash) {
        return sign(hash, "NONE");
    }

    private byte[] signString(String str) {
        return signString(str.getBytes(StandardCharsets.UTF_8));
    }

    public String getSignedEtd(String unsignedEtd) {
        return getSignedEtd(LitackaETD.parse(unsignedEtd)).encode();
    }

    public LitackaETD getSignedEtd(LitackaETD etd) {
        ETDUtils.removeTicketSignature(etd);
        byte[] signature = signString(etd.encode());
        ETDUtils.setTicketSignature(etd, ETDUtils.encodeTicketSignature(signature));
        return etd;
    }

    public byte[] hashActivationToken(byte[] activationToken) {
        return DigestUtils.sha256(activationToken);
    }

    public byte[] signActivationToken(byte[] activationToken) {
        return signString(activationToken);
    }
}
