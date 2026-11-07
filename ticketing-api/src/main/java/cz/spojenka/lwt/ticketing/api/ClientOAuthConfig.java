package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;
import java.util.List;

public record ClientOAuthConfig(String issuerUri, List<String> requiredScopes) implements Serializable {
}
