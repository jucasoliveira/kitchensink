package com.jucasoliveira.kitchensink.catalog.application;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import com.jucasoliveira.kitchensink.catalog.domain.Category;
import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 3.4 — {@code CatalogService}, which is what {@code CatalogHelper} becomes once the JSP bean
 * setters and the EJB-or-DAO fork are taken away from it.
 *
 * <p>Legacy anchor: {@code catalog/client/CatalogHelper.java}. That class held seven getters and a
 * bag of mutable state — {@code start}, {@code count}, {@code locale}, {@code categoryId},
 * {@code productId}, {@code itemId}, {@code searchQuery} — set one property at a time by
 * {@code <c:set>} from a session-scoped {@code <jsp:useBean>} ({@code category.jsp:65-73}). The
 * defaults were {@code start=0} and {@code count=2} ({@code CatalogHelper.java:82-83}), and the
 * screens reset them explicitly whenever {@code param.count} was absent. Here the same seven
 * operations take their parameters, so two requests cannot share a half-set bean.
 *
 * <p>{@code CatalogPageTest} pins the paging arithmetic on its own. This test pins what the service
 * adds on top of the port from 3.2: locale scoping, ordering, and tokenizing the search box.
 *
 * <h2>Three divergences, and why each one</h2>
 *
 * <p><b>Ordering.</b> {@code GET_CATEGORIES} and {@code GET_PRODUCTS} end {@code order by name}
 * ({@code CatalogDAOSQL.xml:75,89}) and that is reproduced, on the requested locale's name.
 * {@code GET_ITEMS} and {@code SEARCH_ITEMS} have no {@code ORDER BY} at all — which is not a
 * contract you can page over, because page 2 is only meaningful relative to a page 1 that was cut
 * from the same order. The legacy survived it by accident: one database, returning insertion order.
 * The service sorts items by id instead. It is lexicographic, so {@code EST-10} precedes
 * {@code EST-6}; ugly on screen, but it invents no structure in the id and it is stable.
 *
 * <p><b>Locale scoping is a filter now, not a join.</b> Every legacy statement carried
 * {@code where b.locale = ?} over a four-table join that also required
 * {@code b.locale = c.locale} ({@code CatalogDAOSQL.xml:114-119}), so a row missing its
 * localised half simply fell out of the result. With {@code Map<String, Details>} on the
 * aggregates, nothing falls out on its own — the service has to drop it, and there is no fallback
 * to {@code en_US}, because the legacy had none either.
 *
 * <p><b>Mixed-case keywords match now, and did not in 2003.</b> This is a fixed defect rather than a
 * preserved behaviour, and it is the one place this test departs from the shipped application on
 * purpose. {@code GenericCatalogDAO.java:361-365} built the pattern as {@code "%" + keywords[i] + "%"}
 * with no {@code toLowerCase()}, and fed it to {@code lower(name) like ?}
 * ({@code CatalogDAOSQL.xml:119}) — so a lowered column was compared against an unlowered pattern
 * and any keyword with a capital in it matched nothing. Searching "Angelfish" in the shipped Pet
 * Store returned an empty page; only "angelfish" worked. {@code CloudscapeCatalogDAO.java:392-400}
 * does lowercase, but it is the disabled DAO: {@code ejb-jar.xml:61} and {@code web.xml:194} both
 * select {@code GenericCatalogDAO}, with the Cloudscape one commented out directly above. The
 * service lowercases in {@code keywords()}, which is where {@code CloudscapeCatalogDAO} put it.
 */
@Tag("parity")
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@Import(TestcontainersConfiguration.class)
class CatalogServiceTest {

	static final String EN = "en_US";

	static final String JA = "ja_JP";

	@Autowired
	CatalogService catalog;

	/**
	 * {@code sidebar.jsp} and {@code category.jsp} — the browse path, which is three list
	 * operations and three by-id lookups, each scoped to a locale.
	 */
	@Nested
	@DisplayName("browse")
	class Browse {

		@Test
		@DisplayName("CatalogDAOSQL.xml:75 order by name — the five categories come back alphabetically by their en_US name, not by id and not by insertion order")
		void categories_are_ordered_by_localised_name() {
			// Seeded in id order FISH, DOGS, REPTILES, CATS, BIRDS; named Fish, Dogs, Reptiles,
			// Cats, Birds. Sorting by name is therefore visible: it agrees with neither the id
			// order nor the file order.
			assertThat(ids(CatalogServiceTest.this.catalog.browseCategories(EN, 0, 99).contents(), Category::id))
				.containsExactly("BIRDS", "CATS", "DOGS", "FISH", "REPTILES");
		}

		@Test
		@DisplayName("the same five categories are there under ja_JP, ordered by the ja_JP name rather than the en_US one")
		void categories_are_ordered_by_the_requested_locales_name() {
			// The order under ja_JP is whatever String.compareTo makes of 鳥/猫/犬/爬虫類/魚, which is
			// code-point order and not a collation anyone chose. Asserting the set rather than the
			// sequence keeps this test about locale scoping; ordering is asserted under en_US above.
			assertThat(ids(CatalogServiceTest.this.catalog.browseCategories(JA, 0, 99).contents(), Category::id))
				.containsExactlyInAnyOrder("BIRDS", "CATS", "DOGS", "FISH", "REPTILES");
		}

		@Test
		@DisplayName("CatalogDAOSQL.xml:89 — the six DOGS products come back by name, so Chihuahua precedes Dalmation and Golden precedes Labrador")
		void products_in_a_category_are_ordered_by_localised_name() {
			assertThat(ids(CatalogServiceTest.this.catalog.browseProducts("DOGS", EN, 0, 99).contents(), Product::id))
				.containsExactly("K9-BD-01", "K9-CW-01", "K9-DL-01", "K9-RT-01", "K9-RT-02", "K9-PO-02");
		}

		@Test
		@DisplayName("an unknown category is an empty page, not an error — the legacy returned EMPTY_PAGE from a query that matched nothing")
		void an_unknown_category_browses_to_nothing() {
			assertThat(CatalogServiceTest.this.catalog.browseProducts("UNICORNS", EN, 0, 99).contents()).isEmpty();
		}

		@Test
		@DisplayName("DIVERGENCE — GET_ITEMS has no ORDER BY, so items are sorted by id here to make paging mean something")
		void items_for_a_product_are_ordered_by_id() {
			assertThat(ids(CatalogServiceTest.this.catalog.browseItems("K9-RT-02", EN, 0, 99).contents(), Item::id))
				.containsExactly("EST-22", "EST-23", "EST-24", "EST-25");
		}

		@Test
		@DisplayName("where b.locale = ? — EST-15 has no ja_JP row, so browsing FL-DSH-01 under ja_JP returns EST-14 alone")
		void browsing_items_drops_rows_that_have_no_row_in_that_locale() {
			// The same seed asymmetry MongoCatalogSearchTest leans on: both items hang off the Manx
			// product, only one was translated. The legacy join dropped EST-15 from every ja_JP
			// result; the filter has to drop it here.
			assertThat(ids(CatalogServiceTest.this.catalog.browseItems("FL-DSH-01", EN, 0, 99).contents(), Item::id))
				.containsExactly("EST-14", "EST-15");
			assertThat(ids(CatalogServiceTest.this.catalog.browseItems("FL-DSH-01", JA, 0, 99).contents(), Item::id))
				.containsExactly("EST-14");
		}

		@Test
		@DisplayName("GET_CATEGORY/GET_PRODUCT/GET_ITEM returned null when the row had no line in that locale, so the lookups are empty there")
		void the_by_id_lookups_are_locale_scoped_too() {
			assertThat(CatalogServiceTest.this.catalog.category("FISH", EN)).isPresent();
			assertThat(CatalogServiceTest.this.catalog.product("FI-SW-01", JA)).isPresent();
			assertThat(CatalogServiceTest.this.catalog.item("EST-14", JA)).isPresent();
			assertThat(CatalogServiceTest.this.catalog.item("EST-15", JA)).isEmpty();
		}

		@Test
		@DisplayName("an unknown id is empty rather than an exception, matching the null the DAO returned when resultSet.first() was false")
		void an_unknown_id_is_empty() {
			assertThat(CatalogServiceTest.this.catalog.category("UNICORNS", EN)).isEmpty();
			assertThat(CatalogServiceTest.this.catalog.product("XX-00-00", EN)).isEmpty();
			assertThat(CatalogServiceTest.this.catalog.item("EST-999", EN)).isEmpty();
		}

		@Test
		@DisplayName("a locale nobody translated into is an empty catalog, not an en_US fallback — the legacy had no fallback and neither does this")
		void an_untranslated_locale_shows_nothing() {
			assertThat(CatalogServiceTest.this.catalog.browseCategories("fr_FR", 0, 99).contents()).isEmpty();
			assertThat(CatalogServiceTest.this.catalog.category("FISH", "fr_FR")).isEmpty();
		}

		@Test
		@DisplayName("CatalogHelper.java:82-83 — count=2 was the screens' default, and two pages of two cover the four FISH products without overlap")
		void browse_pages_at_the_legacy_default_of_two() {
			CatalogPage<Product> first = CatalogServiceTest.this.catalog.browseProducts("FISH", EN, 0, 2);
			CatalogPage<Product> second = CatalogServiceTest.this.catalog.browseProducts("FISH", EN,
					first.startOfNextPage(), 2);

			// Angelfish, Goldfish, Koi, Tiger Shark — by name, which is why FI-FW-02 comes second.
			assertThat(ids(first.contents(), Product::id)).containsExactly("FI-SW-01", "FI-FW-02");
			assertThat(first.hasNext()).isTrue();
			assertThat(ids(second.contents(), Product::id)).containsExactly("FI-FW-01", "FI-SW-02");
			assertThat(second.hasNext()).isFalse();
			assertThat(second.previousPageAvailable()).isTrue();
		}

	}

	/**
	 * {@code search.jsp} — the tokenizing that {@code MongoCatalogSearchTest} deliberately left to
	 * this issue, plus the paging the port cannot do because it returns a whole list.
	 */
	@Nested
	@DisplayName("search")
	class Search {

		@Test
		@DisplayName("GenericCatalogDAO.java:345-349 — StringTokenizer splits the box on whitespace, so 'Golden Retriever' is two keywords")
		void the_query_is_split_on_whitespace() {
			assertThat(CatalogService.keywords("Golden Retriever")).containsExactlyInAnyOrder("golden", "retriever");
		}

		@Test
		@DisplayName("StringTokenizer collapses runs of whitespace, including tabs and newlines, and yields no empty token")
		void runs_of_whitespace_produce_no_empty_keyword() {
			assertThat(CatalogService.keywords("  golden \t\n retriever  ")).containsExactlyInAnyOrder("golden",
					"retriever");
		}

		@Test
		@DisplayName("the tokens went into a HashSet, so a repeated word is one keyword and the SQL gained no extra fragment for it")
		void repeated_words_are_one_keyword() {
			assertThat(CatalogService.keywords("dog dog DOG")).containsExactly("dog");
		}

		@Test
		@DisplayName("DIVERGENCE — keywords are lowercased, closing the defect that made 'Angelfish' match nothing in 2003")
		void keywords_are_lowercased() {
			// GenericCatalogDAO.java:361-365 wrapped the keyword verbatim and compared it to
			// lower(name); CloudscapeCatalogDAO.java:392 lowercased first. ejb-jar.xml:61 and
			// web.xml:194 both deploy the former. This is the latter's behaviour, chosen.
			assertThat(CatalogService.keywords("ANGELFISH")).containsExactly("angelfish");
		}

		@Test
		@DisplayName("GenericCatalogDAO.java:350 — an empty keyword set was Page.EMPTY_PAGE, so a blank box returns nothing rather than the catalog")
		void a_blank_query_returns_an_empty_page() {
			assertThat(CatalogService.keywords("   ")).isEmpty();
			assertThat(CatalogServiceTest.this.catalog.search("", EN, 0, 2).contents()).isEmpty();
			assertThat(CatalogServiceTest.this.catalog.search("   ", EN, 0, 2).contents()).isEmpty();
			assertThat(CatalogServiceTest.this.catalog.search("", EN, 0, 2).hasNext()).isFalse();
		}

		@Test
		@DisplayName("search.jsp:75 set searchQuery from param.keywords, which is null on a first visit — that must not throw")
		void a_null_query_returns_an_empty_page() {
			assertThat(CatalogService.keywords(null)).isEmpty();
			assertThat(CatalogServiceTest.this.catalog.search(null, EN, 0, 2).contents()).isEmpty();
		}

		@Test
		@DisplayName("end to end: a mixed-case query reaches the port lowercased and finds both Angelfish items")
		void a_mixed_case_query_finds_its_items() {
			assertThat(ids(CatalogServiceTest.this.catalog.search("Angelfish", EN, 0, 99).contents(), Item::id))
				.containsExactly("EST-1", "EST-2");
		}

		@Test
		@DisplayName("the VARIABLE fragment is OR'd — 'Iguana Finch' returns both, so two words widen the result rather than narrowing it")
		void two_keywords_widen_the_result() {
			assertThat(ids(CatalogServiceTest.this.catalog.search("Iguana Finch", EN, 0, 99).contents(), Item::id))
				.containsExactly("EST-13", "EST-19");
		}

		@Test
		@DisplayName("DIVERGENCE — search results are sorted by id, so 'dog' pages deterministically; the legacy paged an unordered result set")
		void search_results_are_ordered_by_id() {
			// The thirteen ids MongoCatalogSearchTest asserts as a set, asserted here as a
			// sequence. Lexicographic, so EST-10 leads and EST-6 trails: the price of an order
			// the legacy never specified.
			assertThat(ids(CatalogServiceTest.this.catalog.search("dog", EN, 0, 99).contents(), Item::id))
				.containsExactly("EST-10", "EST-12", "EST-22", "EST-23", "EST-24", "EST-25", "EST-26", "EST-27",
						"EST-28", "EST-6", "EST-7", "EST-8", "EST-9");
		}

		@Test
		@DisplayName("walking the whole 'dog' result two at a time reaches all thirteen items exactly once")
		void paging_a_search_partitions_its_results() {
			// The property that ordering exists to buy. Under the legacy's unordered result set
			// this could only be asserted of one database on one day.
			List<String> all = ids(CatalogServiceTest.this.catalog.search("dog", EN, 0, 99).contents(), Item::id);

			List<String> paged = new ArrayList<>();
			int start = 0;
			CatalogPage<Item> page;
			do {
				page = CatalogServiceTest.this.catalog.search("dog", EN, start, 2);
				paged.addAll(ids(page.contents(), Item::id));
				start = page.startOfNextPage();
			}
			while (page.hasNext());

			assertThat(paged).containsExactlyElementsOf(all);
			assertThat(paged).hasSize(13);
		}

		@Test
		@DisplayName("where b.locale = ? — a ja_JP search finds EST-14 and leaves out EST-15, which has no ja_JP row")
		void search_is_scoped_to_one_locale() {
			assertThat(ids(CatalogServiceTest.this.catalog.search("マンクスネコ", JA, 0, 99).contents(), Item::id))
				.containsExactly("EST-14");
			assertThat(CatalogServiceTest.this.catalog.search("Angelfish", JA, 0, 99).contents()).isEmpty();
		}

		@Test
		@DisplayName("a search that matches nothing is an empty page, and search.jsp:79-81 rendered 'No results were found' from exactly that")
		void a_query_that_matches_nothing_is_an_empty_page() {
			CatalogPage<Item> none = CatalogServiceTest.this.catalog.search("aardvark", EN, 0, 2);

			assertThat(none.contents()).isEmpty();
			assertThat(none.hasNext()).isFalse();
			assertThat(none.previousPageAvailable()).isFalse();
		}

	}

	private static <T> List<String> ids(List<T> rows, Function<T, String> id) {
		return rows.stream().map(id).toList();
	}

}
