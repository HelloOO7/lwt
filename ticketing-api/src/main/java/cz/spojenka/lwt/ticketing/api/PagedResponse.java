package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;
import java.util.List;

public record PagedResponse<T>(List<T> content, PageMetadata page) implements Serializable {
}
