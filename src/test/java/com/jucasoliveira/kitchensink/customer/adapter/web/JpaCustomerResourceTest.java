package com.jucasoliveira.kitchensink.customer.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AGENTS.md §5 — "both persistence profiles stay green" — applied to 4.7's resource, exactly as
 * {@code JpaCatalogResourceTest} applies it to 3.6's.
 *
 * <p>This is the whole bundle in one test, and the reason issues 4.6 and 4.7 were done together:
 * a registration arrives as JSON, goes through {@code CustomerRegistration}, is written by
 * {@code JpaCustomerRepository} into an H2 row, and comes back out through
 * {@code GET /api/customers/{id}} — with nothing in the application above the adapter having been
 * told which store it is talking to. Under {@code mongo}, {@code CustomerResourceTest} runs the
 * same path against a document. That is the claim the MongoDB stretch goal rests on, and this is
 * the smallest end-to-end statement of it.
 *
 * <p>A smoke test on purpose, for {@code JpaCatalogResourceTest}'s reason:
 * {@code CustomerRepositoryContract} already holds the two adapters to the same 15 answers and
 * {@code CustomerResourceTest} pins the HTTP contract, so re-running those here would buy a second
 * copy of the same evidence. What is only provable here is that nothing above the port is
 * store-specific — in particular that {@code CustomerResource} no longer carries the
 * {@code @Profile("mongo")} it had until 4.6, which would leave this context with the customer API
 * unmapped and every request below a 404.
 *
 * <p>No {@link com.jucasoliveira.kitchensink.TestcontainersConfiguration} import, for the reason
 * {@code PersistenceProfileJpaTest} gives: under {@code jpa} there is no Mongo and no Docker.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:customer-api;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("jpa")
class JpaCustomerResourceTest {

	@Autowired
	MockMvc mvc;

	@Test
	@DisplayName("register over the API and read it back — the same slice, against H2 instead of a document")
	void the_customer_slice_answers_under_the_jpa_profile() throws Exception {
		this.mvc.perform(CustomerResourceTest.registration("grace")).andExpect(status().isCreated());

		this.mvc.perform(get("/api/customers/grace").with(user(CustomerResourceTest.READER))
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value("grace"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.contactInfo.address.city").value("London"))
			// The optional field: NULL in an H2 column here, an absent key in a document there.
			// CustomerRepositoryContract flattens the two at the port; this is the same absence
			// arriving at the far end of the stack.
			.andExpect(jsonPath("$.contactInfo.address.streetName2").doesNotExist())
			.andExpect(jsonPath("$.profile.preferredLanguage").value("en_US"))
			.andExpect(content().string(not(containsString("passwordHash"))));
	}

	@Test
	@DisplayName("the duplicate rule is the service's, so it holds against H2 unchanged")
	void a_duplicate_is_still_a_409_under_jpa() throws Exception {
		// Under mongo the second gate is a duplicate _id; here it is JpaCustomerRepository's
		// persist() rather than save(). Different mechanism, same 409, and neither is in the
		// resource — which is what "the rule belongs to the application service" means in practice.
		this.mvc.perform(CustomerResourceTest.registration("ada")).andExpect(status().isCreated());

		this.mvc.perform(CustomerResourceTest.registration("ada")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.title").value("Duplicate Account"));
	}

}
