package com.jucasoliveira.kitchensink.catalog.adapter.web;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import com.jucasoliveira.kitchensink.catalog.LegacyCatalogSeed;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 3.6 — {@code /api/catalog/**}, and its acceptance criterion: "same application service, no
 * view coupling. Proves the layering."
 *
 * <p>Legacy anchor: <strong>none.</strong> {@code grep -rl javax.ws.rs petstore1.3.1_02/src}
 * returns nothing — Pet Store predates REST, and this is the second of the two empty rows in
 * ADR-0006's kitchensink → Pet Store table, after {@code CustomerResource} (1.9). So the parity
 * that can be pinned here is not "the legacy answered this URL"; it is that the JSON channel and
 * the JSP-descended HTML channel of 3.5 answer with the <em>same</em> data, from the same
 * {@code CatalogService}, under the legacy's own defaults.
 *
 * <p>Those defaults are the anchors this class does carry:
 * <ul>
 * <li>{@code start=0}, {@code count=2} — {@code CatalogHelper.java:82-83}, and the
 * {@code <c:otherwise>} the screens reset to when {@code param.count} was absent
 * ({@code category.jsp:66-73}, {@code search.jsp:65-71}). Two rows a page is an odd default for an
 * API; it is here because the REST channel and the Thymeleaf channel paging differently would be a
 * layering claim this suite could not make.</li>
 * <li>{@code locale=en_US} — {@code category.jsp:75}, with {@code ja/category.jsp:73} and
 * {@code zh/category.jsp:73} hardcoding their own. The legacy never negotiated a locale; each
 * docroot was compiled around a constant. A {@code locale} query parameter is the honest
 * translation, and it is why there is no {@code Accept-Language} test below.</li>
 * <li>{@code keywords} — the request parameter {@code search.jsp:75} read.</li>
 * </ul>
 *
 * <p>This runs against MongoDB, the default profile and the one the demo runs on.
 * {@link JpaCatalogResourceTest} is the smoke test that keeps the other profile honest, and it is
 * deliberately not a second copy of this file — the reasoning is {@code CatalogScreenJpaTest}'s,
 * arrived at one issue earlier for the screens: {@code CatalogRepositoryContract} already holds the
 * two adapters to the same answers, so what is left to prove up here is only that nothing in the
 * web adapter is store-specific.
 *
 * <p>Content values come from {@link LegacyCatalogSeed}, which parses a verbatim copy of
 * {@code Populate-UTF8.xml}, so a name or a price asserted here is the 2003 file's, never a literal
 * typed by hand. Ids and page arithmetic are literal: those are structure, not content.
 *
 * <h2>Two deviations this suite pins on purpose</h2>
 *
 * <ol>
 * <li><b>An unknown parent is 404, where the legacy rendered an empty screen.</b>
 * {@code category.jsp} with a bogus {@code category_id} drew a page with an empty table —
 * {@code Page.EMPTY_PAGE} came back from a query that matched nothing, and the JSP had nowhere to
 * report "no such category" to. Over HTTP, {@code 200 []} for a category that does not exist is
 * indistinguishable from a real category with no products, so the resource spends an extra lookup
 * to tell the two apart.</li>
 * <li><b>A missing locale is 404, not an {@code en_US} fallback.</b> Inherited from the service and
 * ultimately from {@code where b.locale = ?} over the four-table join
 * ({@code CatalogDAOSQL.xml:114-119}): a row with no localised half fell out of the result, and
 * there was no fallback to fall back to. {@code CatalogServiceTest} pins this at the service; here
 * it is pinned as a status code.</li>
 * </ol>
 *
 * <p>Tagged {@code parity}: red means the read path stopped answering what the DAO answered, or the
 * two channels stopped agreeing.
 */
@Tag("parity")
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogResourceTest {

	static final String EN = "en_US";

	static final String JA = "ja_JP";

	static LegacyCatalogSeed legacy;

	@Autowired
	MockMvc mvc;

	@BeforeAll
	static void readTheLegacySeed() {
		legacy = LegacyCatalogSeed.read();
	}

	// ----------------------------------------------------------------------------------------
	// browse — sidebar.jsp -> category.jsp -> product.jsp -> item.jsp, as four URLs instead of
	// four screens.
	// ----------------------------------------------------------------------------------------

	@Test
	@DisplayName("CatalogHelper.java:82-83 — GET /api/catalog/categories with no parameters is the legacy default: two rows from index 0, ordered by localised name")
	void the_first_page_of_categories_is_the_legacy_default() throws Exception {
		// Seeded FISH, DOGS, REPTILES, CATS, BIRDS; ordered by en_US name that is Birds, Cats,
		// Dogs, Fish, Reptiles — so the order agrees with neither the id order nor the file's.
		this.mvc.perform(get("/api/catalog/categories"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.contents[*].id").value(contains("BIRDS", "CATS")))
			.andExpect(jsonPath("$.start").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.hasPrevious").value(false))
			.andExpect(jsonPath("$.nextStart").value(2));
	}

	@Test
	@DisplayName("category.jsp:122,133 — start and count walk the five categories in three pages, and the last one says there is no next")
	void paging_walks_the_categories_without_overlap() throws Exception {
		this.mvc.perform(get("/api/catalog/categories").param("start", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("DOGS", "FISH")))
			.andExpect(jsonPath("$.hasPrevious").value(true))
			.andExpect(jsonPath("$.previousStart").value(0))
			.andExpect(jsonPath("$.hasNext").value(true));

		this.mvc.perform(get("/api/catalog/categories").param("start", "4"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("REPTILES")))
			.andExpect(jsonPath("$.size").value(1))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("a start past the end is an empty page rather than a 404 — Page.EMPTY_PAGE, over HTTP")
	void a_start_past_the_end_is_an_empty_page() throws Exception {
		this.mvc.perform(get("/api/catalog/categories").param("start", "99"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents", hasSize(0)))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("GET /api/catalog/categories/{id} answers the localised category, and the locale parameter switches it")
	void a_category_is_localised_by_the_query_parameter() throws Exception {
		LegacyCatalogSeed.Category fish = legacyCategory("FISH");

		this.mvc.perform(get("/api/catalog/categories/FISH"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("FISH"))
			.andExpect(jsonPath("$.name").value(fish.details().get(EN).name()))
			.andExpect(jsonPath("$.image").value(fish.details().get(EN).image()));

		this.mvc.perform(get("/api/catalog/categories/FISH").param("locale", JA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value(fish.details().get(JA).name()));
	}

	@Test
	@DisplayName("the response carries one locale, not the aggregate's whole Map<locale, details> — the view record is what stops the document leaking onto the wire")
	void a_response_never_carries_the_other_locales() throws Exception {
		LegacyCatalogSeed.Category fish = legacyCategory("FISH");

		this.mvc.perform(get("/api/catalog/categories/FISH"))
			.andExpect(status().isOk())
			// Serializing the Category aggregate straight out would have put all three locales
			// here, under a "details" key, and welded the JSON shape to the domain record.
			.andExpect(jsonPath("$.details").doesNotExist())
			.andExpect(content().string(not(containsString(fish.details().get(JA).name()))));
	}

	@Test
	@DisplayName("an unknown id is 404 — GET_CATEGORY returned null, and null has one honest status code")
	void an_unknown_id_is_404() throws Exception {
		this.mvc.perform(get("/api/catalog/categories/UNICORNS"))
			.andExpect(status().isNotFound());
		this.mvc.perform(get("/api/catalog/products/XX-00-00"))
			.andExpect(status().isNotFound());
		this.mvc.perform(get("/api/catalog/items/EST-999"))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("DIVERGENCE — an unknown parent is 404 on the nested lists too, where the legacy drew an empty table")
	void an_unknown_parent_is_404_on_the_nested_lists() throws Exception {
		this.mvc.perform(get("/api/catalog/categories/UNICORNS/products"))
			.andExpect(status().isNotFound());
		this.mvc.perform(get("/api/catalog/products/XX-00-00/items"))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("CatalogDAOSQL.xml:114-119 — a locale nobody translated into is 404, not an en_US fallback, because the legacy join had none")
	void an_untranslated_locale_is_404() throws Exception {
		this.mvc.perform(get("/api/catalog/categories/FISH").param("locale", "fr_FR"))
			.andExpect(status().isNotFound());
		// Same seed asymmetry CatalogServiceTest leans on: both items hang off the Manx
		// product, only EST-14 was translated into ja_JP.
		this.mvc.perform(get("/api/catalog/items/EST-14").param("locale", JA))
			.andExpect(status().isOk());
		this.mvc.perform(get("/api/catalog/items/EST-15").param("locale", JA))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("CatalogDAOSQL.xml:89 order by name — the first page of FISH products is Angelfish then Goldfish, so FI-FW-02 precedes FI-FW-01")
	void products_in_a_category_come_back_by_localised_name() throws Exception {
		LegacyCatalogSeed.Product angelfish = legacyProduct("FI-SW-01");

		this.mvc.perform(get("/api/catalog/categories/FISH/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("FI-SW-01", "FI-FW-02")))
			.andExpect(jsonPath("$.contents[0].name").value(angelfish.details().get(EN).name()))
			.andExpect(jsonPath("$.hasNext").value(true));
	}

	@Test
	@DisplayName("DIVERGENCE — GET_ITEMS had no ORDER BY, so the items of a product are paged by id here to make page 2 mean something")
	void items_for_a_product_are_ordered_by_id() throws Exception {
		this.mvc.perform(get("/api/catalog/products/FI-SW-01/items"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("EST-1", "EST-2")))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("item.jsp — an item view carries its product's name and its own attributes, so the JSON needs no second call to be renderable")
	void an_item_carries_its_product_and_its_attributes() throws Exception {
		LegacyCatalogSeed.Item est1 = legacyItem("EST-1");
		LegacyCatalogSeed.Product angelfish = legacyProduct(est1.productId());

		this.mvc.perform(get("/api/catalog/items/EST-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value("EST-1"))
			.andExpect(jsonPath("$.productId").value(angelfish.id()))
			.andExpect(jsonPath("$.productName").value(angelfish.details().get(EN).name()))
			// GenericCatalogDAO read the attributes into five ATTR columns; the seed's list is
			// joined for display, which is what item.jsp did with them.
			.andExpect(jsonPath("$.attributes").value(String.join(" ", est1.details().get(EN).attributes())))
			.andExpect(jsonPath("$.description").value(est1.details().get(EN).description()));
	}

	@Test
	@DisplayName("prices are formatted in the requested locale, and the seed prices are per-locale too — 16.50 dollars is 1951 yen, not a converted 16.50")
	void prices_are_formatted_in_the_requested_locale() throws Exception {
		LegacyCatalogSeed.Item est1 = legacyItem("EST-1");

		// Populate-UTF8.xml carries a separate price per locale rather than one price and an
		// exchange rate, so formatting per locale is reading the file, not converting it.
		this.mvc.perform(get("/api/catalog/items/EST-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.listPrice").value("$" + est1.details().get(EN).listPrice()));

		// Asserted as a substring: the yen symbol and its spacing are CLDR's to change between
		// JDKs, the grouping of the seed's own 1951 is not.
		this.mvc.perform(get("/api/catalog/items/EST-1").param("locale", JA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.listPrice").value(containsString("1,951")))
			.andExpect(jsonPath("$.productName").value(legacyProduct("FI-SW-01").details().get(JA).name()));
	}

	// ----------------------------------------------------------------------------------------
	// search — search.jsp, whose one input box became one query parameter.
	// ----------------------------------------------------------------------------------------

	@Test
	@DisplayName("search.jsp:75 — the keywords parameter finds a product's items by that product's name")
	void a_product_name_finds_its_items() throws Exception {
		this.mvc.perform(get("/api/catalog/search").param("keywords", "angelfish"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("EST-1", "EST-2")))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("GenericCatalogDAO.java:361-365 — 'Angelfish' with a capital A finds what 'angelfish' finds, which the shipped 2003 app did not")
	void a_capitalised_keyword_matches() throws Exception {
		// The one deliberate defect fix in the read path: the legacy built the LIKE pattern
		// without lower()ing it and compared it against a lower()ed column, so any keyword
		// carrying a capital matched nothing. CatalogService lowercases in keywords().
		this.mvc.perform(get("/api/catalog/search").param("keywords", "Angelfish"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("EST-1", "EST-2")));
	}

	@Test
	@DisplayName("GenericCatalogDAO.java:350 — an empty box was Page.EMPTY_PAGE, so it is 200 with nothing in it, not the whole catalog and not a 400")
	void an_empty_query_is_an_empty_page() throws Exception {
		this.mvc.perform(get("/api/catalog/search"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents", hasSize(0)))
			.andExpect(jsonPath("$.hasNext").value(false));

		this.mvc.perform(get("/api/catalog/search").param("keywords", "   "))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents", hasSize(0)));
	}

	@Test
	@DisplayName("search results page like every other list, on the same start/count pair")
	void search_results_page() throws Exception {
		this.mvc.perform(
				get("/api/catalog/search").param("keywords", "angelfish").param("count", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("EST-1")))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.nextStart").value(1));

		this.mvc.perform(get("/api/catalog/search").param("keywords", "angelfish")
			.param("start", "1")
			.param("count", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("EST-2")))
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.hasPrevious").value(true));
	}

	@Test
	@DisplayName("a keyword from one locale finds nothing in another — the en_US name does not match under ja_JP")
	void keywords_are_locale_scoped() throws Exception {
		this.mvc
			.perform(get("/api/catalog/search").param("keywords", "Angelfish").param("locale", JA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents", hasSize(0)));

		this.mvc.perform(get("/api/catalog/search")
			.param("keywords", legacyProduct("FI-SW-01").details().get(JA).name())
			.param("locale", JA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("EST-1", "EST-2")));
	}

	// ----------------------------------------------------------------------------------------
	// layering — the acceptance criterion itself. Everything above proves the resource answers
	// correctly; these two prove *where* the answer comes from.
	// ----------------------------------------------------------------------------------------

	@Test
	@DisplayName("the screen and the API answer the same page of the same category — one CatalogService, two delivery mechanisms")
	void both_channels_answer_from_the_same_service() throws Exception {
		LegacyCatalogSeed.Product angelfish = legacyProduct("FI-SW-01");
		LegacyCatalogSeed.Product goldfish = legacyProduct("FI-FW-02");
		LegacyCatalogSeed.Product koi = legacyProduct("FI-FW-01");

		// 3.5's Thymeleaf screen, descended from category.jsp.
		this.mvc.perform(get("/catalog/categories/FISH"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(angelfish.details().get(EN).name())))
			.andExpect(content().string(containsString(goldfish.details().get(EN).name())))
			// Page two's first product. Its absence is what makes this a page rather than a list.
			.andExpect(content().string(not(containsString(koi.details().get(EN).name()))));

		// 3.6's resource, same default count, same order, same two products.
		this.mvc.perform(get("/api/catalog/categories/FISH/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("FI-SW-01", "FI-FW-02")))
			.andExpect(jsonPath("$.contents[0].name").value(angelfish.details().get(EN).name()));
	}

	@Test
	@DisplayName("browsing needs no sign-on, on either channel — the legacy put the catalog in front of the login, and SecurityConfig keeps it there")
	void the_catalog_is_anonymous() throws Exception {
		// signon-config.xml protected the cart and the account, never category.jsp. A 3xx to a
		// login form here would be a migration that quietly moved the paywall.
		this.mvc.perform(get("/api/catalog/categories")).andExpect(status().isOk());
		this.mvc.perform(get("/api/catalog/items/EST-1")).andExpect(status().isOk());
		this.mvc.perform(get("/catalog")).andExpect(status().isOk());
	}

	private static LegacyCatalogSeed.Category legacyCategory(String id) {
	return legacy.categories.stream().filter(it -> it.id().equals(id)).findFirst().orElseThrow();
	}

	private static LegacyCatalogSeed.Product legacyProduct(String id) {
	return legacy.products.stream().filter(it -> it.id().equals(id)).findFirst().orElseThrow();
	}

	private static LegacyCatalogSeed.Item legacyItem(String id) {
	return legacy.items.stream().filter(it -> it.id().equals(id)).findFirst().orElseThrow();
	}

}
