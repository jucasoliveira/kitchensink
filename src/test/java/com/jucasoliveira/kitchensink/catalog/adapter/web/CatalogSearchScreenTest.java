package com.jucasoliveira.kitchensink.catalog.adapter.web;

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
 * {@code search.screen} — the last of {@code mappings.xml}'s screens to get a migrated twin.
 *
 * <p>{@code banner.jsp:57-60} put a {@code keywords} box on every page and posted it here, and
 * {@code SEARCH_ITEMS} ({@code CatalogDAOSQL.xml:118}) answered it. The service and the REST facade
 * for it landed with issues 3.4 and 3.6; the *screen* did not, so the migrated app could search
 * over HTTP and offered no way to do it in a browser.
 *
 * <p>The screen deliberately holds no logic of its own: it calls the same
 * {@code CatalogService.search} the REST resource calls, so the two channels cannot drift and the
 * divergences {@code CatalogSearchContract} pins — {@code %} is a literal here where {@code LIKE}
 * made it a wildcard, matching is a substring rather than a word, results carry no promised order —
 * are stated once, for both.
 */
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogSearchScreenTest {

	@Autowired
	MockMvc mvc;

	@Test
	@Tag("parity")
	@DisplayName("SEARCH_ITEMS (CatalogDAOSQL.xml:118) — a keyword finds the items the DAO would have found")
	void a_keyword_finds_items() throws Exception {
		this.mvc.perform(get("/catalog/search").param("keywords", "angelfish"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("EST-1")))
			.andExpect(content().string(containsString("Angelfish")));
	}

	@Test
	@DisplayName("the box is on every page, as banner.jsp put it there")
	void the_search_box_is_in_the_banner() throws Exception {
		// On the catalogue, on the registration screen, and on the results page itself — the
		// legacy banner was bound to every <screen> in screendefinitions_en_US.xml.
		for (String url : new String[] { "/catalog", "/customers", "/catalog/search" }) {
			this.mvc.perform(get(url))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("action=\"/catalog/search\"")))
				.andExpect(content().string(containsString("name=\"keywords\"")));
		}
	}

	@Test
	@DisplayName("a search that matches nothing says so, and says what it searched for")
	void no_matches_is_explained() throws Exception {
		// search.jsp echoed the keywords back. Without that a zero-result page is
		// indistinguishable from one that never ran a search — the same failure mode as the
		// unseeded sidebar.
		this.mvc.perform(get("/catalog/search").param("keywords", "nosuchanimal"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("nosuchanimal")))
			.andExpect(content().string(containsString("No items matched")));
	}

	@Test
	@DisplayName("an empty box prompts rather than reporting zero results")
	void an_empty_query_prompts() throws Exception {
		this.mvc.perform(get("/catalog/search"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Type a keyword")))
			.andExpect(content().string(not(containsString("No items matched"))));
	}

	@Test
	@DisplayName("the box carries a label, even though it does not show one")
	void the_search_box_is_labelled() throws Exception {
		// Visually hidden, present for a screen reader. banner.jsp had no label at all, and an
		// unlabelled search box is the same defect the register form had.
		this.mvc.perform(get("/catalog"))
			.andExpect(content().string(containsString("for=\"keywords\"")))
			.andExpect(content().string(containsString("id=\"keywords\"")));
	}

	@Test
	@Tag("parity")
	@DisplayName("ja_JP — search runs in the requested locale, and the button is the legacy's 検索")
	void search_is_localised() throws Exception {
		this.mvc.perform(get("/catalog/search").param("keywords", "angelfish").param("locale", "ja_JP"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("検索")));
	}

}
