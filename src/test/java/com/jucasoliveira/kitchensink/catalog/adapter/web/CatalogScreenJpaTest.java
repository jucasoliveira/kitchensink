package com.jucasoliveira.kitchensink.catalog.adapter.web;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AGENTS.md §5 — "both persistence profiles stay green, or the gap is written down". The screens of
 * 3.5 sit behind {@code CatalogService}, so under {@code jpa} they must render the same catalogue
 * out of H2 that {@link CatalogScreenTest} renders out of MongoDB.
 *
 * <p>This is a smoke test on purpose. {@code CatalogRepositoryContract} already holds the two
 * adapters to the same answers, and {@link CatalogScreenTest} pins the screen behaviour; repeating
 * all of it here would buy a second copy of the same evidence. What is only provable here is that
 * nothing in the web adapter is store-specific — in particular that {@code CatalogController}
 * carries no {@code @Profile("mongo")}, which {@code CustomerController} does and which would make
 * this context start without a single catalog screen mapped.
 *
 * <p>No {@link com.jucasoliveira.kitchensink.TestcontainersConfiguration} import, for the reason
 * {@code PersistenceProfileJpaTest} gives: under {@code jpa} there is no Mongo and no Docker.
 */
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@AutoConfigureMockMvc
@ActiveProfiles("jpa")
class CatalogScreenJpaTest {

	@Autowired
	MockMvc mvc;

	@Test
	@DisplayName("the category screen renders the same first page out of H2 as it does out of MongoDB")
	void the_category_screen_renders_under_the_jpa_profile() throws Exception {
		this.mvc.perform(get("/catalog/categories/FISH").locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("data-slot=\"sidebar\"")))
			.andExpect(content().string(containsString("Angelfish")))
			.andExpect(content().string(containsString("Goldfish")))
			.andExpect(content().string(containsString("/catalog/products/FI-SW-01")));
	}

	@Test
	@DisplayName("\"/\" is the store front under jpa too — HomeController carries no @Profile")
	void the_store_front_is_reachable_under_the_jpa_profile() throws Exception {
		// This one is here because it was once false. HomeController carried @Profile("mongo"), so
		// under jpa it was never registered and Boot's WelcomePageHandlerMapping served the spike's
		// index.html instead — a 200 with a "Members" heading and no controller behind it at all.
		// 7.4 demos the profile switch by running the same journey against both stores, and the
		// journey starts at "/", so the gap sat on the first click. Same reason the class javadoc
		// gives for CatalogController: a @Profile on a web adapter is a screen that only half
		// exists.
		this.mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/catalog"));

		this.mvc.perform(get("/catalog").locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Welcome to the BluePrints Petstore")));
	}

	@Test
	@DisplayName("so does the item screen, prices and product name included")
	void the_item_screen_renders_under_the_jpa_profile() throws Exception {
		this.mvc.perform(get("/catalog/items/EST-1").locale(Locale.US))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Angelfish")))
			.andExpect(content().string(containsString("$16.50")));
	}

}
