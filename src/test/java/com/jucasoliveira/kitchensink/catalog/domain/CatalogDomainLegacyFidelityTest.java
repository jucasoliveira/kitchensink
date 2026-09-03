package com.jucasoliveira.kitchensink.catalog.domain;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.jucasoliveira.kitchensink.catalog.LegacyCatalogSeed;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 3.1 — {@link Category}, {@link Product} and {@link Item} hold exactly what
 * {@link LegacyCatalogSeed} read from the legacy seed, entity by entity and locale by locale.
 *
 * <p>No mapper exists yet (that's 3.2), so this test builds the domain records inline from the
 * fixture. It pins the shape of {@code *_details.locale} before the persistence adapter is
 * written against it, including the DTDs' {@code (CategoryDetails+)} / {@code (ProductDetails+)}
 * / {@code (ItemDetails+)} cardinality: one or more rows, not exactly three.
 */
@Tag("parity")
class CatalogDomainLegacyFidelityTest {

    static LegacyCatalogSeed seed;

    @BeforeAll
    static void readTheLegacySeed() {
        seed = LegacyCatalogSeed.read();
    }

    @Test
    @DisplayName("category_details — FISH carries its en_US/ja_JP/zh_CN rows unchanged into Category")
    void category_details_survive_the_move() {
        LegacyCatalogSeed.Category fish = seed.categories.stream().filter(c -> c.id().equals("FISH")).findFirst()
                .orElseThrow();
        Category category = toCategory(fish);

        assertThat(category.id()).isEqualTo("FISH");
        assertThat(category.details().keySet()).containsExactlyInAnyOrder("en_US", "ja_JP", "zh_CN");
        assertThat(category.details().get("en_US").name()).isEqualTo(fish.details().get("en_US").name());
    }

    @Test
    @DisplayName("product_details — FI-SW-01 carries its category reference and locale rows into Product")
    void product_details_and_category_reference_survive_the_move() {
        LegacyCatalogSeed.Product angelfish = seed.products.stream().filter(p -> p.id().equals("FI-SW-01"))
                .findFirst().orElseThrow();
        Product product = toProduct(angelfish);

        assertThat(product.categoryId()).isEqualTo("FISH");
        assertThat(product.details().keySet()).containsExactlyInAnyOrder("en_US", "ja_JP", "zh_CN");
        assertThat(product.details().get("zh_CN").description()).isEqualTo(angelfish.details().get("zh_CN").description());
    }

    @Test
    @DisplayName("item_details.listprice — EST-1 is 16.50 in en_US and 1951 in ja_JP, not a currency conversion")
    void item_prices_are_per_locale() {
        LegacyCatalogSeed.Item est1 = seed.items.stream().filter(i -> i.id().equals("EST-1")).findFirst().orElseThrow();
        Item item = toItem(est1);

        assertThat(item.productId()).isEqualTo(est1.productId());
        assertThat(item.details().get("en_US").listPrice()).isEqualTo("16.50");
        assertThat(item.details().get("ja_JP").listPrice()).isEqualTo("1951");
        assertThat(item.details().get("en_US").attributes()).containsExactly("Large", "Cuddly");
    }

    @Test
    @DisplayName("Item.dtd (ItemDetails+) — EST-15 (Populate-UTF8.xml:882) has no ja_JP row, and Item.details() simply lacks the key")
    void a_missing_locale_row_stays_missing() {
        LegacyCatalogSeed.Item est15 = seed.items.stream().filter(i -> i.id().equals("EST-15")).findFirst().orElseThrow();
        Item item = toItem(est15);

        assertThat(item.details()).doesNotContainKey("ja_JP");
        assertThat(item.details().get("ja_JP")).isNull();
    }

    private static Category toCategory(LegacyCatalogSeed.Category c) {
        Map<String, CategoryDetails> details = new LinkedHashMap<>();
        c.details().forEach((locale, d) -> details.put(locale, new CategoryDetails(d.name(), d.image(), d.description())));
        return new Category(c.id(), details);
    }

    private static Product toProduct(LegacyCatalogSeed.Product p) {
        Map<String, ProductDetails> details = new LinkedHashMap<>();
        p.details().forEach((locale, d) -> details.put(locale, new ProductDetails(d.name(), d.image(), d.description())));
        return new Product(p.id(), p.categoryId(), details);
    }

    private static Item toItem(LegacyCatalogSeed.Item i) {
        Map<String, ItemDetails> details = new LinkedHashMap<>();
        i.details().forEach((locale, d) -> details.put(locale,
                new ItemDetails(d.listPrice(), d.unitCost(), d.attributes(), d.image(), d.description())));
        return new Item(i.id(), i.productId(), details);
    }

}
