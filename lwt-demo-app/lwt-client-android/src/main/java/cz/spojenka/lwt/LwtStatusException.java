package cz.spojenka.lwt;

import java.io.IOException;

public class LwtStatusException extends IOException {

    private final int statusCode;

    public LwtStatusException(int statusCode) {
        super("LWT operation failed with status code: " + statusCode);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
