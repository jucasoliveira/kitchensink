package com.jucasoliveira.kitchensink.catalog.adapter.web;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the catalog looks like before anyone has seeded it.
 *
 * <p>Every other screen test runs with {@code kitchensink.seed.catalog=true}, so the unseeded
 * state had never been rendered by a test — and it turns out to be the first thing a new developer
 * sees, because {@code scripts/run.sh} does not seed and only {@code scripts/seed.sh} does. The
 * sidebar rendered its heading and an empty {@code <ul>}, which reads as a broken page rather than
 * an empty database. This class exists because that is a first-run experience, and first-run
 * experiences are worth a test.
 *
 * <p>The legacy had the same trap and the same answer: {@code PopulateServlet} existed precisely
 * because the catalog tables started empty, and {@code GET /Populate?forcefully=true} is what
 * {@code scripts/seed.sh} replaces.
 *
 * <p>Note the absent property: this is the only screen test that does <em>not</em> set
 * {@code kitchensink.seed.catalog}, which means it gets its own application context. That is the
 * cost of the test and the reason there is one class rather than a method on {@code
 * CatalogScreenTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmptyCatalogTest {

	@Autowired
	MockMvc mvc;

	@Test
	@DisplayName("an unseeded catalog says so, and names the command that fixes it")
	void the_sidebar_explains_an_empty_catalogue() throws Exception {
		this.mvc.perform(get("/catalog"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("No categories yet")))
			.andExpect(content().string(containsString("scripts/seed.sh")));
	}

	@Test
	@DisplayName("and it is still a 200 with working chrome — empty is not broken")
	void an_empty_catalogue_is_not_an_error() throws Exception {
		// The page must stay navigable: an empty store is a normal state, not a failure, so the
		// banner and its sign-on links have to render regardless of what is in the database.
		this.mvc.perform(get("/catalog"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("href=\"/login\"")))
			.andExpect(content().string(containsString("banner_logo.gif")));
	}

}
