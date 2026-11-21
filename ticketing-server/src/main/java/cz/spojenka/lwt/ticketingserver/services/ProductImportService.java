package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketing.api.ProductCategory;
import cz.spojenka.lwt.ticketing.api.ProductValidityType;
import cz.spojenka.lwt.ticketingserver.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

@Service
public class ProductImportService {

    private static final Logger logger = LoggerFactory.getLogger(ProductImportService.class);

    private static final String DESTINATION_NAME = "products";

    @Value("${tickets.products-import-path}")
    private String sourcePath;

    private byte[] currentDataChecksum;
    private Instant currentDataTimestamp;

    private final DataImportService importService;
    private final ProductRepository repository;

    public ProductImportService(DataImportService importService, ProductRepository repository) {
        this.importService = importService;
        this.repository = repository;
    }

    @PostConstruct
    public void checkForImport() throws IOException {
        importService.checkAndImport(Path.of(sourcePath), DESTINATION_NAME, new DataImportService.ImportCallback() {
            @Override
            public void onImport(Path path, byte[] checksum) {
                logger.info("Importing products from {}", path);
                currentDataChecksum = checksum;
                CsvMapper mapper = CsvMapper.shared();
                CsvSchema schema = CsvSchema
                        .builder()
                        .setUseHeader(true)
                        .setColumnSeparator(';')
                        .build();

                try (MappingIterator<CsvProduct> in = mapper.readerFor(CsvProduct.class).with(schema).readValues(path)) {
                    in.forEachRemaining(ProductImportService.this::importProduct);
                }
                logger.info("Products import finished");
            }

            @Override
            public void onNotModified(byte[] checksum, Instant lastModified) {
                currentDataChecksum = checksum;
                currentDataTimestamp = lastModified;
            }
        });
    }

    public byte[] getCurrentDataChecksum() {
        return currentDataChecksum;
    }

    public Instant getCurrentDataTimestamp() {
        return currentDataTimestamp;
    }

    private void importProduct(CsvProduct product) {
        // if the ID already exists, it will be overwritten
        repository.save(createProductFromImport(product));
    }

    private static Product createProductFromImport(CsvProduct imported) {
        return new Product(
                imported.productId(),
                importProductCategory(imported.productCategory()),
                imported.cptp(),
                imported.title(),
                imported.price(),
                imported.vatRate(),
                imported.validFrom(),
                imported.validTo(),
                imported.validityMinutes(),
                importValidityType(imported.validityType()),
                imported.zoneCount(),
                imported.eligibleZones(),
                imported.excludedZones(),
                imported.excludesTrains(),
                imported.notice()
        );
    }

    private static ProductCategory importProductCategory(CsvProductCategory imported) {
        return switch (imported) {
            case DOSPELY -> ProductCategory.ADULT;
            case ZVYHODNENE -> ProductCategory.REDUCED;
            case SENIOR -> ProductCategory.SENIOR;
            case ZTP -> ProductCategory.DISABLED;
            case PES -> ProductCategory.DOG;
            case KOLO -> ProductCategory.BIKE;
            case ZAVAZADLO -> ProductCategory.LUGGAGE;
            case LANOVKA -> ProductCategory.CABLE_CAR;
            case SKUPINOVA_3 -> ProductCategory.GROUP_3;
            case SKUPINOVA_6 -> ProductCategory.GROUP_6;
        };
    }

    private static ProductValidityType importValidityType(CsvValidityType imported) {
        return switch (imported) {
            case NULL, REGULAR -> ProductValidityType.REGULAR;
            case TO_MIDNIGHT -> ProductValidityType.TO_MIDNIGHT;
        };
    }
}
