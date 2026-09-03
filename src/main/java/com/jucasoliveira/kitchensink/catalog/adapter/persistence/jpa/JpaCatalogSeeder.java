package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * The relational twin of {@code CatalogSeeder} — same fixture, same property, six tables instead
 * of three collections.
 *
 * <p>Legacy anchor: {@code PopulateServlet.java:158-162}, which with {@code forcefully=true}
 * dropped, recreated and refilled the catalog tables from {@code Populate-UTF8.xml}. The fixture
 * here is {@code seed/catalog.json}, generated from that file by
 * {@code scripts/extract-catalog-seed.py}, so both adapters are seeded from one source and the
 * parity tests can hold them to the same numbers.
 *
 * <p>{@code deleteAll()}, not {@code deleteAllInBatch()}: a bulk {@code delete from category}
 * leaves the {@code category_details} rows behind and trips their foreign key. Deleting entity by
 * entity lets Hibernate clear each element collection first — 49 rows, so the cost is nothing and
 * the semantics match the Mongo seeder's {@code dropCollection}.
 */
@Component
@Profile("jpa")
@ConditionalOnProperty(name = "kitchensink.seed.catalog", havingValue = "true")
public class JpaCatalogSeeder implements ApplicationRunner {

    private static final String FIXTURE = "seed/catalog.json";

    private final CategoryEntityRepository categories;

    private final ProductEntityRepository products;

    private final ItemEntityRepository items;

    private final ObjectMapper json;

    JpaCatalogSeeder(CategoryEntityRepository categories, ProductEntityRepository products,
            ItemEntityRepository items, ObjectMapper json) {
        this.categories = categories;
        this.products = products;
        this.items = items;
        this.json = json;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        Fixture fixture = this.json.readValue(
                new ClassPathResource(FIXTURE).getContentAsString(StandardCharsets.UTF_8), Fixture.class);

        this.items.deleteAll();
        this.products.deleteAll();
        this.categories.deleteAll();

        this.categories.saveAll(fixture.categories().stream()
                .map(category -> new CategoryEntity(category.id(), details(category.details())))
                .toList());
        this.products.saveAll(fixture.products().stream()
                .map(product -> new ProductEntity(product.id(), product.categoryId(), details(product.details())))
                .toList());
        this.items.saveAll(fixture.items().stream()
                .map(item -> new ItemEntity(item.id(), item.productId(), itemDetails(item.details())))
                .toList());
    }

    private static Map<String, DetailsRow> details(Map<String, DetailsJson> rows) {
        Map<String, DetailsRow> mapped = new LinkedHashMap<>();
        rows.forEach((locale, row) -> mapped.put(locale, new DetailsRow(row.name(), row.image(), row.description())));

        return mapped;
    }

    private static Map<String, ItemDetailsRow> itemDetails(Map<String, ItemDetailsJson> rows) {
        Map<String, ItemDetailsRow> mapped = new LinkedHashMap<>();
        rows.forEach((locale, row) -> mapped.put(locale, new ItemDetailsRow(row.listPrice(), row.unitCost(),
                row.attributes(), row.image(), row.description())));

        return mapped;
    }

    /** The fixture's shape. {@code _id} is Mongo's spelling of the primary key, kept in the file. */
    record Fixture(List<CategoryJson> categories, List<ProductJson> products, List<ItemJson> items) {
    }

    record CategoryJson(@JsonProperty("_id") String id, Map<String, DetailsJson> details) {
    }

    record ProductJson(@JsonProperty("_id") String id, String categoryId, Map<String, DetailsJson> details) {
    }

    record ItemJson(@JsonProperty("_id") String id, String productId, Map<String, ItemDetailsJson> details) {
    }

    record DetailsJson(String name, String image, String description) {
    }

    record ItemDetailsJson(String listPrice, String unitCost, List<String> attributes, String image,
            String description) {
    }

}
