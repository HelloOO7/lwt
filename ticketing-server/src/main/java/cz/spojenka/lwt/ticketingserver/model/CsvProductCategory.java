package cz.spojenka.lwt.ticketingserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CsvProductCategory {
    @JsonProperty("Dospělý")
    DOSPELY,
    @JsonProperty("Lanovka")
    LANOVKA,
    @JsonProperty("Kolo")
    KOLO,
    @JsonProperty("Senior")
    SENIOR,
    @JsonProperty("Skupinová 3")
    SKUPINOVA_3,
    @JsonProperty("Skupinová 6")
    SKUPINOVA_6,
    @JsonProperty("Zavazadlo")
    ZAVAZADLO,
    @JsonProperty("ZTP")
    ZTP,
    @JsonProperty("Pes")
    PES,
    @JsonProperty("Zvýhodněné")
    ZVYHODNENE
}
