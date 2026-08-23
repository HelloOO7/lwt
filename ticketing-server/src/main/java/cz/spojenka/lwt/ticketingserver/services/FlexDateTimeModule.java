package cz.spojenka.lwt.ticketingserver.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Component
public class FlexDateTimeModule extends SimpleModule {

    public FlexDateTimeModule(@Value("${spring.jackson.time-zone}") String timeZoneId) {
        ZoneId timeZone = ZoneId.of(timeZoneId);
        addDeserializer(OffsetDateTime.class, new StdDeserializer<>(OffsetDateTime.class) {
            @Override
            public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws JacksonException {
                JsonToken token = jsonParser.currentToken();
                if (token != JsonToken.VALUE_STRING) {
                    throw new tools.jackson.databind.ext.javatime.DateTimeParseException(jsonParser, "Expected string but got " + token, jsonParser.getValueAsString(), OffsetDateTime.class, null);
                }
                String jsonValue = jsonParser.getValueAsString();
                try {
                    return OffsetDateTime.parse(jsonValue);
                } catch (DateTimeParseException e) {
                    return LocalDateTime.parse(jsonValue).atZone(timeZone).toOffsetDateTime();
                }
            }
        });
    }
}
