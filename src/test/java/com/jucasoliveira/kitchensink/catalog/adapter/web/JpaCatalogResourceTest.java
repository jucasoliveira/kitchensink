package com.jucasoliveira.kitchensink.catalog.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AGENTS.md §5 — "both persistence profiles stay green, or the gap is written down", applied to
 * 3.6's resource exactly as {@link CatalogScreenJpaTest} applies it to 3.5's screens.
 *
 * <p>A smoke test on purpose, and for that class's reason: {@code CatalogRepositoryContract} already
 * holds the two adapters to the same answers and {@link CatalogResourceTest} pins the HTTP contract,
 * so re-running nineteen assertions against H2 would buy a second copy of the same evidence. What is
 * only provable here is that nothing in {@code CatalogResource} is store-specific — in particular
 * that it carries no {@code @Profile("mongo")}, which {@code CustomerResource} does and which would
 * leave this context with the catalog API unmapped and every request below a 404.
 *
 * <p>The annotations match {@code CatalogScreenJpaTest}'s exactly, which is not tidiness: an
 * identical configuration means Spring hands both classes the same cached context, so this file
 * adds no second H2 database to the {@code jpa} run and cannot trip the {@code ddl-auto=create-drop}
 * hazard {@code JpaCatalogRepositoryTest} documents.
 *
 * <p>No {@link com.jucasoliveira.kitchensink.TestcontainersConfiguration} import, for the reason
 * {@code PersistenceProfileJpaTest} gives: under {@code jpa} there is no Mongo and no Docker.
 */
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@AutoConfigureMockMvc
@ActiveProfiles("jpa")
class JpaCatalogResourceTest {

	@Autowired
	MockMvc mvc;

	@Test
	@DisplayName("the products of a category come back out of H2 in the same order, on the same two-per-page default")
	void a_nested_list_answers_under_the_jpa_profile() throws Exception {
		this.mvc.perform(get("/api/catalog/categories/FISH/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("FI-SW-01", "FI-FW-02")))
			.andExpect(jsonPath("$.contents[0].name").value("Angelfish"))
			.andExpect(jsonPath("$.hasNext").value(true));
	}

	@Test
	@DisplayName("so does an item, product name and formatted price included")
	void an_item_answers_under_the_jpa_profile() throws Exception {
		this.mvc.perform(get("/api/catalog/items/EST-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.productName").value("Angelfish"))
			.andExpect(jsonPath("$.listPrice").value("$16.50"));
	}

	@Test
	@DisplayName("and so does search, which is the one read the two adapters implement least alike")
	void search_answers_under_the_jpa_profile() throws Exception {
		// Mongo runs an aggregation with a $lookup onto products; the JPA side is a join in JPQL.
		// CatalogSearchContract pins that they agree — this only pins that the resource reaches
		// whichever one is wired.
		this.mvc.perform(get("/api/catalog/search").param("keywords", "angelfish"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contents[*].id").value(contains("EST-1", "EST-2")));
	}

}
