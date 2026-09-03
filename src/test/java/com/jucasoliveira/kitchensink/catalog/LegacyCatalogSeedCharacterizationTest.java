package com.jucasoliveira.kitchensink.catalog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 2.1 — what the legacy catalog seed contains, pinned as numbers before any loader exists.
 *
 * <p>Legacy anchor: {@code apps/petstore/src/docroot/populate/Populate-UTF8.xml:204-1198}. The
 * acceptance criterion for 2.1 is "same category/product/item counts per locale as the legacy
 * seed", and this class is the left-hand side of that comparison. It reads the seed through
 * {@link LegacyCatalogSeed} — a verbatim copy of the legacy file on the test classpath, kept honest by
 * {@link LegacySeedCopyIsVerbatimTest}; the loader test reads the right-hand side from the store and
 * asserts equality with the same figures.
 *
 * <p>It is tagged {@code parity} because it characterizes the legacy, not the new code: if it
 * ever goes red, either the copy of the seed was edited (it must not be — refresh it with cp) or
 * the fixture reader stopped reading the seed the way {@code PopulateServlet} did.
 */
@Tag("parity")
class LegacyCatalogSeedCharacterizationTest {

	/** The three locales the seed carries; {@code zh_CN} is data-only in the deliverable (ADR-0006). */
	static final Set<String> LOCALES = Set.of("en_US", "ja_JP", "zh_CN");

	static LegacyCatalogSeed seed;

	@BeforeAll
	static void readTheLegacySeed() {
		seed = LegacyCatalogSeed.read();
	}

	@Test
	@DisplayName("Populate-UTF8.xml:205-1197 — 5 categories, 16 products, 28 items")
	void entity_counts() {
		assertThat(seed.categories).hasSize(5);
		assertThat(seed.products).hasSize(16);
		assertThat(seed.items).hasSize(28);
	}

	@Test
	@DisplayName("Populate-UTF8.xml:206-275 — the five categories, in seed order")
	void the_categories() {
		assertThat(seed.categories).extracting(LegacyCatalogSeed.Category::id)
				.containsExactly("FISH", "DOGS", "REPTILES", "CATS", "BIRDS");
	}

	@Test
	@DisplayName("XMLDBHandler.java:179-180 — locale keys are stored as en_US / ja_JP / zh_CN, never en-US")
	void locale_keys_use_the_underscore_form() {
		Set<String> seen = seed.categories.stream().flatMap(c -> c.details().keySet().stream())
				.collect(Collectors.toSet());
		seen.addAll(seed.products.stream().flatMap(p -> p.details().keySet().stream()).toList());
		seen.addAll(seed.items.stream().flatMap(i -> i.details().keySet().stream()).toList());
		assertThat(seen).isEqualTo(LOCALES);
	}

	@Test
	@DisplayName("category_details / product_details — every category and product has a row for all three locales")
	void category_and_product_details_per_locale() {
		for (String locale : LOCALES) {
			assertThat(seed.categories).as("category_details rows for %s", locale)
					.allMatch(c -> c.details().containsKey(locale));
			assertThat(seed.products).as("product_details rows for %s", locale)
					.allMatch(p -> p.details().containsKey(locale));
		}
	}

	@Test
	@DisplayName("item_details — 28 en_US, 27 ja_JP, 28 zh_CN: EST-15 (Populate-UTF8.xml:882) has no Japanese row")
	void item_details_per_locale() {
		Map<String, Long> rowsPerLocale = seed.items.stream().flatMap(i -> i.details().keySet().stream())
				.collect(Collectors.groupingBy(l -> l, Collectors.counting()));
		assertThat(rowsPerLocale).containsExactlyInAnyOrderEntriesOf(Map.of("en_US", 28L, "ja_JP", 27L, "zh_CN", 28L));

		List<String> withoutJapanese = seed.items.stream().filter(i -> !i.details().containsKey("ja_JP"))
				.map(LegacyCatalogSeed.Item::id).toList();
		assertThat(withoutJapanese).containsExactly("EST-15");
	}

	@Test
	@DisplayName("Product.dtd / Item.dtd — category and product references are IDREFs and all resolve")
	void references_resolve() {
		Set<String> categoryIds = seed.categories.stream().map(LegacyCatalogSeed.Category::id).collect(Collectors.toSet());
		Set<String> productIds = seed.products.stream().map(LegacyCatalogSeed.Product::id).collect(Collectors.toSet());
		assertThat(seed.products).extracting(LegacyCatalogSeed.Product::categoryId).allMatch(categoryIds::contains);
		assertThat(seed.items).extracting(LegacyCatalogSeed.Item::productId).allMatch(productIds::contains);
	}

	@Test
	@DisplayName("Populate-UTF8.xml — ids are unique within each entity, as the primary keys in PopulateSQL.xml require")
	void ids_are_unique() {
		assertThat(seed.categories).extracting(LegacyCatalogSeed.Category::id).doesNotHaveDuplicates();
		assertThat(seed.products).extracting(LegacyCatalogSeed.Product::id).doesNotHaveDuplicates();
		assertThat(seed.items).extracting(LegacyCatalogSeed.Item::id).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("item_details.listprice is per locale — EST-1 lists at 16.50 in en_US and 1951 in ja_JP, not a conversion")
	void prices_are_per_locale_not_converted() {
		LegacyCatalogSeed.Item est1 = seed.items.stream().filter(i -> i.id().equals("EST-1")).findFirst().orElseThrow();
		assertThat(est1.details().get("en_US").listPrice()).isEqualTo("16.50");
		assertThat(est1.details().get("ja_JP").listPrice()).isEqualTo("1951");
		assertThat(est1.details().get("zh_CN").listPrice()).isEqualTo("142");
		assertThat(est1.details().get("en_US").attributes()).containsExactly("Large", "Cuddly");
	}

}
