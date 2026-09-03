package com.jucasoliveira.kitchensink.catalog.adapter.web;

import java.util.Locale;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 3.5 — the Thymeleaf layout and the four catalog screens: what
 * {@code screendefinitions_en_US.xml} and {@code template.jsp} become once the WAF's screen-name
 * indirection is taken away.
 *
 * <h2>The composition being preserved</h2>
 *
 * <p>The framework's own template ({@code waf/src/docroot/template.jsp:42-56}) has exactly four
 * insert points — {@code title}, {@code banner}, {@code sidebar}, {@code body}, {@code footer} —
 * and every screen in {@code screendefinitions_en_US.xml:45-92} binds the same banner, sidebar and
 * footer while swapping only {@code body}. Pet Store's copy of the template
 * ({@code petstore/src/docroot/template.jsp:69-88}) adds two more slots, {@code mylist} and
 * {@code advicebanner}, and both are fed by the cart and the recommendation engine — T3 under
 * ADR-0006, designed and deliberately unbuilt. So the layout here carries four slots, and
 * {@link #the_four_slots_of_the_waf_template_compose_every_screen} asserts the absence of the other
 * two as loudly as the presence of these: a slot that renders empty because its data is missing is
 * a different thing from a slot that was never migrated.
 *
 * <p>The screens map one-to-one onto the {@code <screen>} elements:
 * {@code main.screen} → {@code GET /catalog}, {@code category.screen?category_id=X} →
 * {@code GET /catalog/categories/X}, {@code product.screen?product_id=X} →
 * {@code GET /catalog/products/X}, {@code item.screen?item_id=X} → {@code GET /catalog/items/X}.
 * {@code search.screen} is 3.6's, not this issue's.
 *
 * <h2>What the paging numbers are</h2>
 *
 * <p>{@code category.jsp:66-73} and {@code product.jsp:66-73} are the same eight lines twice: if
 * {@code param.count} is absent, reset the session bean to {@code start=0, count=2}; otherwise take
 * the request's. {@code sidebar.jsp:57-58} sets {@code count=5, start=0} for the category list. Those
 * three numbers are the whole of the legacy's paging policy and they are asserted here as request
 * defaults, because the bean they used to live on is gone (3.4).
 *
 * <h2>Two deliberate departures</h2>
 *
 * <p><b>An unknown id is a 404.</b> {@code CatalogHelper} returned null and the JSP rendered a
 * table with no rows, so {@code category.screen?category_id=NOSUCH} was a 200 with a heading and
 * nothing under it. That is not a status code you can build a client on.
 *
 * <p><b>The main screen has links, not an image map.</b> {@code main.jsp:44-67} was a
 * {@code <map>} of six {@code <area>} elements over {@code splash.gif} — five categories, with
 * BIRDS given two hotspots ({@code main.jsp:45,60}). The screen keeps the splash image and puts
 * the same five destinations in text links beside it.
 */
@Tag("parity")
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogScreenTest {

	/** The four screens of {@code screendefinitions_en_US.xml}, in the order a shopper meets them. */
	static final String[] SCREENS = { "/catalog", "/catalog/categories/FISH", "/catalog/products/FI-SW-01",
			"/catalog/items/EST-1" };

	@Autowired
	MockMvc mvc;

	@Test
	@DisplayName("waf/template.jsp:42-56 — banner, sidebar, body and footer compose every screen; mylist and advicebanner are gone")
	void the_four_slots_of_the_waf_template_compose_every_screen() throws Exception {
		for (String screen : SCREENS) {
			this.mvc.perform(get(screen).locale(Locale.US))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-slot=\"banner\"")))
				.andExpect(content().string(containsString("data-slot=\"sidebar\"")))
				.andExpect(content().string(containsString("data-slot=\"body\"")))
				.andExpect(content().string(containsString("data-slot=\"footer\"")))
				// ADR-0006: the two personalization slots belong to deferred work, and an empty
				// <div data-slot="mylist"> would be a claim that they were migrated.
				.andExpect(content().string(not(containsString("data-slot=\"mylist\""))))
				.andExpect(content().string(not(containsString("data-slot=\"advicebanner\""))));
		}
	}

	@Test
	@DisplayName("screendefinitions_en_US.xml:45-92 — each screen keeps its own title through the shared layout")
	void each_screen_carries_its_own_title() throws Exception {
		assertTitle("/catalog", "Welcome to the BluePrints Petstore");
		assertTitle("/catalog/categories/FISH", "Items");
		assertTitle("/catalog/products/FI-SW-01", "Product");
		assertTitle("/catalog/items/EST-1", "Item");
	}

	@Test
	@DisplayName("sidebar.jsp:57-58 — the sidebar lists the categories on every screen, count=5 start=0")
	void the_sidebar_lists_every_category_on_every_screen() throws Exception {
		// Five categories and a count of 5: the legacy number is exactly the number of rows, which
		// is why the sidebar never paged. Asserting all five on the deepest screen proves the
		// fragment is in the layout rather than in one controller method.
		this.mvc.perform(get("/catalog/items/EST-1").locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/catalog/categories/BIRDS")))
			.andExpect(content().string(containsString("/catalog/categories/CATS")))
			.andExpect(content().string(containsString("/catalog/categories/DOGS")))
			.andExpect(content().string(containsString("/catalog/categories/FISH")))
			.andExpect(content().string(containsString("/catalog/categories/REPTILES")));
	}

	@Test
	@DisplayName("main.jsp:44-67 — the six-area image map becomes five category links")
	void the_main_screen_offers_every_category() throws Exception {
		this.mvc.perform(get("/catalog").locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Birds")))
			.andExpect(content().string(containsString("Reptiles")))
			.andExpect(content().string(containsString("/catalog/categories/FISH")));
	}

	@Test
	@DisplayName("category.jsp:66-73 — with no parameters the category screen shows two products and offers the next page")
	void the_category_screen_defaults_to_two_products() throws Exception {
		// FISH holds four products; sorted by name (3.4) that is Angelfish, Goldfish | Koi, Tiger Shark.
		this.mvc.perform(get("/catalog/categories/FISH").locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Angelfish")))
			.andExpect(content().string(containsString("Goldfish")))
			.andExpect(content().string(not(containsString("Tiger Shark"))))
			.andExpect(content().string(containsString("start=2")))
			.andExpect(content().string(containsString("count=2")))
			.andExpect(content().string(not(containsString("start=0"))));
	}

	@Test
	@DisplayName("the second page carries a previous link and no next")
	void the_last_page_offers_only_a_previous_link() throws Exception {
		this.mvc.perform(get("/catalog/categories/FISH").param("start", "2").param("count", "2").locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Koi")))
			.andExpect(content().string(containsString("Tiger Shark")))
			.andExpect(content().string(not(containsString("Angelfish"))))
			.andExpect(content().string(containsString("start=0")))
			.andExpect(content().string(not(containsString("start=4"))));
	}

	@Test
	@DisplayName("product.jsp:66-73 — the product screen pages its items on the same two-per-page default")
	void the_product_screen_defaults_to_two_items() throws Exception {
		// K9-RT-02 (Labrador Retriever) holds four items; sorted by id, EST-22..EST-25.
		this.mvc.perform(get("/catalog/products/K9-RT-02").locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("EST-22")))
			.andExpect(content().string(containsString("EST-23")))
			.andExpect(content().string(not(containsString("EST-24"))))
			.andExpect(content().string(containsString("start=2")));
	}

	@Test
	@DisplayName("item.jsp:58-60 — the item screen titles itself with the item's attributes and the product's name")
	void the_item_screen_names_the_item_by_attribute_and_product() throws Exception {
		// The legacy read both off one denormalized CatalogHelper bean. Here ItemDetails has no
		// name at all, so the controller has to fetch the product the item belongs to — the one
		// place a screen needs two aggregates.
		this.mvc.perform(get("/catalog/items/EST-1").locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Large")))
			.andExpect(content().string(containsString("Cuddly")))
			.andExpect(content().string(containsString("Angelfish")));
	}

	@Test
	@DisplayName("item.jsp:62-70 — list price and your price are currency-formatted, as fmt:formatNumber type=currency did")
	void the_item_screen_formats_both_prices_as_currency() throws Exception {
		// The domain carries prices as the strings the legacy XML held ("16.50"), so the formatting
		// has to happen on the way to the screen or it does not happen at all.
		this.mvc.perform(get("/catalog/items/EST-1").locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("$16.50")))
			.andExpect(content().string(containsString("$10.00")));
	}

	@Test
	@DisplayName("the screens chain: main -> category -> product -> item, each link resolving to the next screen")
	void the_screens_link_from_the_front_page_down_to_an_item() throws Exception {
		this.mvc.perform(get("/catalog").locale(Locale.US))
			.andExpect(content().string(containsString("/catalog/categories/FISH")));
		this.mvc.perform(get("/catalog/categories/FISH").locale(Locale.US))
			.andExpect(content().string(containsString("/catalog/products/FI-SW-01")));
		this.mvc.perform(get("/catalog/products/FI-SW-01").locale(Locale.US))
			.andExpect(content().string(containsString("/catalog/items/EST-1")));
		this.mvc.perform(get("/catalog/items/EST-1").locale(Locale.US)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("banner.jsp:81,92,103 — the three flags are the supported locales; ja_JP renders the Japanese catalogue")
	void a_supported_locale_renders_that_locale_s_details() throws Exception {
		this.mvc.perform(get("/catalog/categories/FISH").locale(Locale.JAPAN))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("エンゼルフィッシュ")))
			.andExpect(content().string(not(containsString("Angelfish"))));
	}

	@Test
	@DisplayName("an unsupported locale falls back to en_US, the legacy default of changelocale.do")
	void an_unsupported_locale_falls_back_to_en_us() throws Exception {
		// There is no fr_FR row anywhere in the catalogue; the legacy would have shown a shopper
		// with a French browser the English catalogue, because the locale came from the session
		// (default en_US) and never from Accept-Language.
		this.mvc.perform(get("/catalog/categories/FISH").locale(Locale.FRANCE))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Angelfish")));
	}

	@Test
	@DisplayName("departure — an unknown id is a 404 where CatalogHelper returned an empty table")
	void an_unknown_id_is_not_found() throws Exception {
		this.mvc.perform(get("/catalog/categories/NOSUCH").locale(Locale.US)).andExpect(status().isNotFound());
		this.mvc.perform(get("/catalog/products/NOSUCH").locale(Locale.US)).andExpect(status().isNotFound());
		this.mvc.perform(get("/catalog/items/NOSUCH").locale(Locale.US)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("signon-config.xml — the catalogue is not a protected URL, so every screen is anonymous")
	void the_catalogue_is_readable_without_signing_in() throws Exception {
		// The legacy protected exactly four URLs (signon-config.xml:53-77) and none of them was a
		// catalog screen: browsing came before the account. With anyRequest().authenticated() in
		// the chain, forgetting the permitAll turns all four screens into a redirect to /login.
		for (String screen : SCREENS) {
			this.mvc.perform(get(screen).locale(Locale.US)).andExpect(status().isOk());
		}
	}

	private void assertTitle(String screen, String title) throws Exception {
		this.mvc.perform(get(screen).locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("<title>" + title + "</title>")));
	}

}
