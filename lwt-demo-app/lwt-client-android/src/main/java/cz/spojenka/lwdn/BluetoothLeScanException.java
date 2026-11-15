package cz.spojenka.lwdn;

public class BluetoothLeScanException extends RuntimeException {

    private final int errorCode;

    public BluetoothLeScanException(int errorCode) {
        super("LE scan error: " + errorCode);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
