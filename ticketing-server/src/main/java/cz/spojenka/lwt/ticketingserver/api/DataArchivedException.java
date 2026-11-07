package cz.spojenka.lwt.ticketingserver.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GONE)
public class DataArchivedException extends RuntimeException {

    public DataArchivedException(String message) {
        super(message);
    }
}
