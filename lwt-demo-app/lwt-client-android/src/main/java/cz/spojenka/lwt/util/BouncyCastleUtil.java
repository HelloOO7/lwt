package cz.spojenka.lwt.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

public class BouncyCastleUtil {

    private static final BouncyCastleProvider BC_PROVIDER;

    static {
        Security.removeProvider("BC");
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        BC_PROVIDER = (BouncyCastleProvider) Security.getProvider("BC");
    }

    public static BouncyCastleProvider getBcProvider() {
        return BC_PROVIDER;
    }
}
