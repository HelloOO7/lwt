package cz.spojenka.lwdn;

public class LwdnScanException extends Exception {

    private final ScanErrorCode code;

    public LwdnScanException(ScanErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public LwdnScanException(ScanErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public LwdnScanException(ScanErrorCode code, Throwable cause) {
        super(cause);
        this.code = code;
    }

    public LwdnScanException(ScanErrorCode code) {
        super(code.toString());
        this.code = code;
    }

    public ScanErrorCode getCode() {
        return code;
    }
}
