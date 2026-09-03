package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import java.util.List;
import java.util.Set;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import com.jucasoliveira.kitchensink.catalog.application.CatalogRepository;
import com.jucasoliveira.kitchensink.catalog.domain.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 3.2 — {@code SEARCH_ITEMS}, the seventh statement, and the only one that cannot lose its
 * {@code Locale} parameter.
 *
 * <p>Legacy anchor: {@code CatalogDAOSQL.xml:112-127} and {@code GenericCatalogDAO.java:343-395}.
 * The statement is assembled at runtime from three fragments — a fixed head, a fragment repeated
 * {@code occurrence="VARIABLE"} once per keyword, and a closing paren — over the same four-table
 * join as {@code GET_ITEM}:
 *
 * <pre>
 * where b.locale = ? and ((lower(name)   like ?     -- product_details.name
 *                       or lower(catid)  like ?     -- product.catid
 *                       or lower(b.descn) like ?)   -- item_details.descn
 *                      or (… the same three, per further keyword …))
 * </pre>
 *
 * <p>So: three columns, OR'd; keywords OR'd with each other, never AND'd; each keyword wrapped
 * {@code "%" + keyword + "%"} ({@code GenericCatalogDAO.java:361-365}) — substring, not word —
 * and both sides lower-cased. Those four properties are what the tests below pin, because they
 * are what a user of the 2003 search actually experienced.
 *
 * <h2>What the Mongo adapter does instead, and why</h2>
 *
 * <p>Not {@code $text}. A text index is word-stemmed, single-per-collection and cannot express
 * {@code like '%dog%'} — under it, "dog" would not find the DOGS category — and it has no useful
 * analyzer for the {@code ja_JP} and {@code zh_CN} rows this catalog is half made of. The
 * faithful shape is a case-insensitive regex over the same three fields, reached by one
 * aggregation that {@code $lookup}s {@code items → products}: the join the legacy needed for the
 * projection is still needed for the predicate.
 *
 * <p>One deliberate divergence, asserted at the bottom: keywords are quoted, so a {@code %} typed
 * into the search box is a literal here and was a wildcard in 2003.
 *
 * <p>Tokenizing is <em>not</em> tested here — {@code GenericCatalogDAO.java:345-349} splits the
 * query on whitespace with a {@code StringTokenizer} and dedupes through a {@code HashSet}, which
 * is 3.4's job as the service builds the {@code Collection<String>} this port takes. What the port
 * owes 3.4 is that an empty collection means an empty result, which is the last test here.
 */
@Tag("parity")
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@Import(TestcontainersConfiguration.class)
class MongoCatalogSearchTest {

	static final String EN = "en_US";

	static final String JA = "ja_JP";

	@Autowired
	CatalogRepository catalog;

	@Test
	@DisplayName("lower(name) like '%angelfish%' — a product name finds that product's items")
	void a_keyword_matches_the_product_name() {
		assertThat(ids(this.catalog.searchItems(Set.of("Angelfish"), EN))).containsExactlyInAnyOrder("EST-1", "EST-2");
	}

	@Test
	@DisplayName("lower(name) like lower(?) — matching is case-insensitive on both sides, as the legacy lower()s made it")
	void matching_is_case_insensitive() {
		List<String> lower = ids(this.catalog.searchItems(Set.of("angelfish"), EN));
		List<String> upper = ids(this.catalog.searchItems(Set.of("ANGELFISH"), EN));

		assertThat(lower).containsExactlyInAnyOrder("EST-1", "EST-2");
		assertThat(upper).containsExactlyInAnyOrderElementsOf(lower);
	}

	@Test
	@DisplayName("'%' + keyword + '%' (GenericCatalogDAO.java:361-365) — 'elfish' is a substring of Angelfish and a word in nothing, and still matches")
	void a_keyword_matches_a_substring_not_a_word() {
		// The distinction that rules $text out. A stemmed word index would return nothing here;
		// LIKE '%elfish%' returned Angelfish, and so must the regex.
		assertThat(ids(this.catalog.searchItems(Set.of("elfish"), EN))).containsExactlyInAnyOrder("EST-1", "EST-2");
	}

	@Test
	@DisplayName("lower(catid) like '%dog%' — the category id is searchable, so 'dog' returns all twelve DOGS items plus the rattlesnake that doubles as a watch dog")
	void a_keyword_matches_the_category_id() {
		// EST-6..EST-10, EST-22..EST-28 are DOGS (catid branch; EST-6/EST-7 also match on the
		// product name "Bulldog"). EST-12 is a REPTILES item whose item_details.descn reads
		// "Doubles as a watch dog" — the descn branch, and the reason this assertion is exact
		// rather than a contains: it is the one case where all three columns are in play at once.
		assertThat(ids(this.catalog.searchItems(Set.of("dog"), EN))).containsExactlyInAnyOrder("EST-6", "EST-7",
				"EST-8", "EST-9", "EST-10", "EST-12", "EST-22", "EST-23", "EST-24", "EST-25", "EST-26", "EST-27",
				"EST-28");
	}

	@Test
	@DisplayName("lower(b.descn) like '%yapper%' — the item description is searchable on its own")
	void a_keyword_matches_the_item_description() {
		// "Little yapper" appears in no product name and no category id, so a hit here can only
		// have come from item_details.descn.
		assertThat(ids(this.catalog.searchItems(Set.of("yapper"), EN))).containsExactly("EST-26");
	}

	@Test
	@DisplayName("the VARIABLE fragment (CatalogDAOSQL.xml:122) is OR'd, not AND'd — two keywords widen the result, they do not narrow it")
	void keywords_are_ored_never_anded() {
		List<String> both = ids(this.catalog.searchItems(Set.of("Iguana", "Finch"), EN));

		assertThat(both).containsExactlyInAnyOrder("EST-13", "EST-19");
		assertThat(both).containsAll(ids(this.catalog.searchItems(Set.of("Iguana"), EN)));
		assertThat(both).containsAll(ids(this.catalog.searchItems(Set.of("Finch"), EN)));
	}

	@Test
	@DisplayName("two keywords that share a hit return it once, not twice")
	void a_document_matching_twice_is_returned_once() {
		// "Bulldog" matches the product name and "dog" matches the same rows through catid; the
		// legacy got de-duplication free from the row-per-item join, and an adapter that $lookup'd
		// or $unwound carelessly would not.
		assertThat(ids(this.catalog.searchItems(Set.of("Bulldog", "dog"), EN)))
				.doesNotHaveDuplicates()
				.contains("EST-6", "EST-7");
	}

	@Test
	@DisplayName("where b.locale = ? — a ja_JP product name searched under ja_JP finds EST-14, and EST-15 stays out because it has no ja_JP row")
	void search_is_scoped_to_one_locale() {
		// マンクスネコ is FL-DSH-01's ja_JP name; both EST-14 and EST-15 hang off that product, but
		// EST-15 has no ja_JP item_details row (Populate-UTF8.xml:882). The legacy join
		// (b.locale = ? and b.locale = c.locale) dropped it from the ja_JP result set for exactly
		// that reason, and the adapter has to drop it too — matching the product's ja_JP name is
		// not enough on its own.
		assertThat(ids(this.catalog.searchItems(Set.of("マンクスネコ"), JA))).containsExactly("EST-14");
		assertThat(ids(this.catalog.searchItems(Set.of("Manx"), EN))).containsExactlyInAnyOrder("EST-14", "EST-15");
	}

	@Test
	@DisplayName("a keyword from one locale finds nothing in another: the en_US name does not match under ja_JP")
	void a_keyword_does_not_leak_across_locales() {
		assertThat(this.catalog.searchItems(Set.of("Angelfish"), JA)).isEmpty();
		assertThat(ids(this.catalog.searchItems(Set.of("エンゼルフィッシュ"), JA))).containsExactlyInAnyOrder("EST-1", "EST-2");
	}

	@Test
	@DisplayName("no ORDER BY on SEARCH_ITEMS — the legacy returned result-set order, so the port promises a set, not a sequence")
	void search_results_carry_no_promised_order() {
		// Stated as a test so that 3.4 sorts explicitly rather than inheriting whatever Mongo
		// happens to return, the way the JSPs inherited whatever Cloudscape happened to return.
		List<String> once = ids(this.catalog.searchItems(Set.of("dog"), EN));
		List<String> twice = ids(this.catalog.searchItems(Set.of("dog"), EN));

		assertThat(twice).containsExactlyInAnyOrderElementsOf(once);
	}

	@Test
	@DisplayName("DIVERGENCE — '%' was a LIKE wildcard in 2003 and is a literal here, so it matches nothing")
	void a_percent_sign_is_a_literal_now() {
		// Under the legacy statement "%" + "%" + "%" made every non-null row match. Quoting the
		// keyword closes that, and closes the injection of ( ) [ ] . * that a raw regex would open.
		assertThat(this.catalog.searchItems(Set.of("%"), EN)).isEmpty();
		assertThat(this.catalog.searchItems(Set.of(".*"), EN)).isEmpty();
	}

	@Test
	@DisplayName("GenericCatalogDAO.java:350 — no keywords was Page.EMPTY_PAGE, and stays an empty list rather than the whole catalog")
	void no_keywords_matches_nothing() {
		assertThat(this.catalog.searchItems(Set.of(), EN)).isEmpty();
	}

	private static List<String> ids(List<Item> items) {
		return items.stream().map(Item::id).toList();
	}

}
