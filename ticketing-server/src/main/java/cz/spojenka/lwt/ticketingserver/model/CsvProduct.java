package cz.spojenka.lwt.ticketingserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;

public record CsvProduct(
        @JsonProperty("ProductID") int productId,
        @JsonProperty("ProductCategory") CsvProductCategory productCategory,
        @JsonProperty("title") String title,
        @JsonProperty("price") float price,
        @JsonProperty("vat_rate") float vatRate,
        @JsonProperty("ValidFrom") LocalDate validFrom,
        @JsonProperty("ValidTo") LocalDate validTo,
        @JsonProperty("ValidityMinutes") int validityMinutes,
        @JsonProperty("ValidityType") CsvValidityType validityType,
        @JsonProperty("EligibleZones") String eligibleZones,
        @JsonProperty("ZoneCount") Integer zoneCount,
        @JsonProperty("ExcludedZones") String excludedZones,
        @JsonProperty("ExcludesTrains") @JsonDeserialize(using = CsvProduct.BoolDeserializer.class) boolean excludesTrains,
        @JsonProperty("cptp") int cptp,
        @JsonProperty("notice") String notice
) {

    public static class BoolDeserializer extends ValueDeserializer<Boolean> {

        @Override
        public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            return p.getString().equals("1");
        }
    }
}
