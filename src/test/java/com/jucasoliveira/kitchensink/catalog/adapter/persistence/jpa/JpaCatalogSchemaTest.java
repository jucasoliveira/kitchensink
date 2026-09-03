package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import java.util.List;

import com.jucasoliveira.kitchensink.catalog.LegacyCatalogSeed;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 3.3 — the relational mirror of {@code CatalogSeedLoadTest}, and the evidence behind the
 * sentence "six tables became three collections".
 *
 * <p>Legacy anchor: {@code PopulateSQL.xml:60-158} creates {@code category}, {@code category_details},
 * {@code product}, {@code product_details}, {@code item} and {@code item_details}; every read
 * statement in {@code CatalogDAOSQL.xml} joins across them, and {@code GET_ITEM:92} needs four of
 * them at once with {@code b.locale = c.locale} restated as a join predicate.
 *
 * <p>The Mongo adapter collapsed that into three collections with the {@code *_details} rows
 * embedded per locale. The JPA adapter deliberately does <em>not</em>: its
 * {@code @ElementCollection} + {@code @MapKeyColumn("locale")} mappings put the six tables back,
 * with the legacy's own column spellings. That is what makes the profile switch worth
 * demonstrating — under {@code jpa} the store really is the 2003 shape, so the demo compares two
 * data models rather than two drivers.
 *
 * <p>Asserted through {@link JdbcTemplate} rather than through the entities on purpose: an
 * assertion made through the mapper would pass whatever tables Hibernate happened to generate.
 * Row counts come from {@link LegacyCatalogSeed}, never from a literal, so this class and
 * {@code CatalogSeedLoadTest} are pinned to the same file and cannot drift apart.
 *
 * <p>Shares its context (and its H2 database) with {@link JpaCatalogRepositoryTest} — same
 * properties, same profile.
 */
@Tag("parity")
@SpringBootTest(properties = { "kitchensink.seed.catalog=true",
		"spring.datasource.url=jdbc:h2:mem:catalog-parity;DB_CLOSE_DELAY=-1" })
@ActiveProfiles("jpa")
class JpaCatalogSchemaTest {

	private static LegacyCatalogSeed legacy;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeAll
	static void readTheLegacySeed() {
		legacy = LegacyCatalogSeed.read();
	}

	@Test
	@DisplayName("PopulateSQL.xml:60-158 — all six catalog tables are back, under the names the legacy gave them")
	void the_six_legacy_tables_exist() {
		assertThat(tables()).contains("CATEGORY", "CATEGORY_DETAILS", "PRODUCT", "PRODUCT_DETAILS", "ITEM",
				"ITEM_DETAILS");
	}

	@Test
	@DisplayName("the entity tables hold the legacy seed's rows: 5 categories, 16 products, 28 items")
	void the_entity_tables_hold_the_seeded_rows() {
		assertThat(rowsIn("category")).isEqualTo(legacy.categories.size());
		assertThat(rowsIn("product")).isEqualTo(legacy.products.size());
		assertThat(rowsIn("item")).isEqualTo(legacy.items.size());
	}

	@Test
	@DisplayName("the *_details tables hold one row per locale — the rows the Mongo adapter embedded")
	void the_details_tables_hold_one_row_per_locale() {
		// 15 / 48 / 83 as the seed stands. Computed, not typed: the numbers are a property of
		// Populate-UTF8.xml, and if that file's locale coverage ever changed, both this test and
		// CatalogSeedLoadTest should follow it rather than have to be edited.
		assertThat(rowsIn("category_details"))
				.isEqualTo(legacy.categories.stream().mapToInt(c -> c.details().size()).sum());
		assertThat(rowsIn("product_details"))
				.isEqualTo(legacy.products.stream().mapToInt(p -> p.details().size()).sum());
		assertThat(rowsIn("item_details"))
				.isEqualTo(legacy.items.stream().mapToInt(i -> i.details().size()).sum());
	}

	@Test
	@DisplayName("one aggregate is several rows here and one document there: FISH is 1 + 3, and its Mongo twin is 1")
	void an_aggregate_is_spread_across_two_tables() {
		assertThat(this.jdbc.queryForObject("select count(*) from category where catid = 'FISH'", Integer.class))
				.isEqualTo(1);
		assertThat(this.jdbc.queryForObject("select count(*) from category_details where catid = 'FISH'",
				Integer.class)).isEqualTo(3);
		assertThat(this.jdbc.queryForList("select locale from category_details where catid = 'FISH'", String.class))
				.containsExactlyInAnyOrder("en_US", "ja_JP", "zh_CN");
	}

	@Test
	@DisplayName("Populate-UTF8.xml:882 — a missing locale row is a missing row here, not a null column")
	void a_missing_locale_row_is_simply_absent() {
		// The same gap CatalogRepositoryContract asserts through the port, seen in the store: EST-15
		// has two item_details rows and no ja_JP one. Under the legacy join that absence removed
		// EST-15 from every ja_JP result set, which is the behaviour JpaCatalogSearchTest pins.
		assertThat(this.jdbc.queryForList("select locale from item_details where itemid = 'EST-15'", String.class))
				.containsExactlyInAnyOrder("en_US", "zh_CN");
	}

	@Test
	@DisplayName("Item.dtd's Attribute+ is five columns again (PopulateSQL.xml:146-158), not a seventh table")
	void the_item_attributes_are_flattened_into_five_columns() {
		// A collection cannot nest inside an @ElementCollection value, so the mapping has to do
		// what the legacy did — and the legacy's reason was the same one, a decade earlier. The
		// domain keeps List<String>; the flattening lives in the adapter and is asserted here so
		// that it stays a documented mapping rather than an accident.
		assertThat(columnsOf("ITEM_DETAILS")).contains("ATTR1", "ATTR2", "ATTR3", "ATTR4", "ATTR5");
		assertThat(tables()).noneMatch(table -> table.contains("ATTRIBUTE"));
		assertThat(this.jdbc.queryForObject(
				"select attr1 from item_details where itemid = 'EST-1' and locale = 'en_US'", String.class))
				.isEqualTo(legacyItemAttributes().get(0));
	}

	private List<String> tables() {
		return this.jdbc.queryForList(
				"select table_name from information_schema.tables where table_schema = 'PUBLIC'", String.class);
	}

	private List<String> columnsOf(String table) {
		return this.jdbc.queryForList(
				"select column_name from information_schema.columns where table_name = ?", String.class, table);
	}

	private Integer rowsIn(String table) {
		return this.jdbc.queryForObject("select count(*) from " + table, Integer.class);
	}

	private static List<String> legacyItemAttributes() {
		return legacy.items.stream().filter(item -> item.id().equals("EST-1")).findFirst().orElseThrow()
				.details().get("en_US").attributes();
	}

}
