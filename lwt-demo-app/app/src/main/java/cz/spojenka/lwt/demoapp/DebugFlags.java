package cz.spojenka.lwt.demoapp;

public class DebugFlags {

    public static boolean isAllowUntrustedCertificates() {
        return Boolean.TRUE.toString().equals(SystemProperties.read("debug.cz.spojenka.lwt.security.allowUntrustedCertificates"));
    }
}
