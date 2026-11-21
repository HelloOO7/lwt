package cz.spojenka.lwt.ticketingserver.model;

import cz.dpp.praguepublictransport.LitackaUtils;
import cz.spojenka.lwt.ticketing.api.ProductCategory;
import cz.spojenka.lwt.ticketing.api.ProductValidityType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.time.LocalDate;
import java.util.List;

@Entity
public class Product {

    @Id
    private int id;
    private ProductCategory category;
    private int cptp;
    private String title;

    private float price;
    private float vatRate;

    private LocalDate validFrom;
    private LocalDate validTo;

    private int validityMinutes;
    private ProductValidityType validityType;

    private Integer zoneCount;
    private String eligibleZones;
    private String excludedZones;
    private boolean excludesTrains;
    private String notice;

    Product() {

    }

    public Product(
            int id,
            ProductCategory category,
            int cptp,
            String title,
            float price,
            float vatRate,
            LocalDate validFrom,
            LocalDate validTo,
            int validityMinutes,
            ProductValidityType validityType,
            Integer zoneCount,
            String eligibleZones,
            String excludedZones,
            boolean excludesTrains,
            String notice
    ) {
        this.id = id;
        this.category = category;
        this.cptp = cptp;
        this.title = title;
        this.price = price;
        this.vatRate = vatRate;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.validityMinutes = validityMinutes;
        this.validityType = validityType;
        this.zoneCount = zoneCount;
        this.eligibleZones = eligibleZones;
        this.excludedZones = excludedZones;
        this.excludesTrains = excludesTrains;
        this.notice = notice;
    }

    public int getId() {
        return id;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public int getCptp() {
        return cptp;
    }

    public String getTitle() {
        return title;
    }

    public float getPrice() {
        return price;
    }

    public float getVatRate() {
        return vatRate;
    }

    public int getValidityMinutes() {
        return validityMinutes;
    }

    public ProductValidityType getValidityType() {
        return validityType;
    }

    public Integer getZoneCount() {
        return zoneCount;
    }

    public String getEligibleZonesString() {
        return eligibleZones;
    }

    public List<String> getEligibleZones() {
        return LitackaUtils.parseCommaSeparatedList(eligibleZones);
    }

    public String getExcludedZonesString() {
        return excludedZones;
    }

    public List<String> getExcludedZones() {
        return LitackaUtils.parseCommaSeparatedList(excludedZones);
    }

    public boolean isExcludesTrains() {
        return excludesTrains;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public String getNotice() {
        return notice;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("category", category)
                .append("cptp", cptp)
                .append("title", title)
                .append("price", price)
                .append("vatRate", vatRate)
                .append("validFrom", validFrom)
                .append("validTo", validTo)
                .append("validityMinutes", validityMinutes)
                .append("validityType", validityType)
                .append("zoneCount", zoneCount)
                .append("eligibleZones", eligibleZones)
                .append("excludedZones", excludedZones)
                .append("excludesTrains", excludesTrains)
                .append("notice", notice)
                .toString();
    }
}
