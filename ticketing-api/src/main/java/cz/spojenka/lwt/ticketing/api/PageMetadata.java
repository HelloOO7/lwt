package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;

public record PageMetadata(int size, long totalElements, int totalPages, int number) implements Serializable {
}
