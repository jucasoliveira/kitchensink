package com.jucasoliveira.kitchensink.customer.adapter.web;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 1.9 — the REST half of the walking skeleton.
 *
 * <p>Legacy anchor: <strong>none.</strong> {@code grep -rl javax.ws.rs src} over the 2003 tree
 * returns nothing; Pet Store predates REST. This is one of the two empty rows in ADR-0006's
 * kitchensink → Pet Store table ({@code MemberResourceRESTService} ↔ nothing), and this class is
 * the first written evidence of a capability the legacy application never had. Issue 4.7
 * promotes the tracer bullet to the full resource (GET by id, per-field 400 detail).
 *
 * <p>What is actually under test is the acceptance criterion's second clause: both channels go
 * "through the same application service, proving the view layer is not load-bearing". So the
 * last two tests register on one channel and read on the other.
 *
 * <p>No request here carries a CSRF token: {@code /api/**} is meant to be called by a client
 * that has no session, so the chain exempts it. That is a deliberate, small hole and 4.7 should
 * close it properly with a separate stateless filter chain for {@code /api/**}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerResourceTest {

	static final String PASSWORD = "s3cret";

	@Autowired
	MockMvc mvc;

	@Autowired
	MongoTemplate template;

	@BeforeEach
	void reset() {
		this.template.dropCollection("customers");
	}

	@Test
	@DisplayName("POST /api/customers registers and answers 201 with the customer, never the credential")
	void a_valid_registration_is_created() throws Exception {
		this.mvc.perform(registration("ada"))
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.userId").value("ada"))
			// CustomerEJB.java:78 — the account is created active, and the API says so.
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.contactInfo.givenName").value("Ada"))
			.andExpect(jsonPath("$.contactInfo.address.city").value("London"))
			// Finding #1, over the wire: no hash, no password, under any key.
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(content().string(not(containsString("$2a$"))))
			.andExpect(content().string(not(containsString(PASSWORD))));

		assertThat(this.template.getCollection("customers").countDocuments()).isEqualTo(1);
	}

	@Test
	@DisplayName("GET /api/customers lists what was registered, as JSON")
	void the_list_is_json() throws Exception {
		this.mvc.perform(registration("ada")).andExpect(status().isCreated());
		this.mvc.perform(registration("grace")).andExpect(status().isCreated());

		this.mvc.perform(get("/api/customers").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[*].userId").value(org.hamcrest.Matchers.containsInAnyOrder("ada", "grace")))
			.andExpect(content().string(not(containsString("passwordHash"))));
	}

	@Test
	@DisplayName("GET /api/customers on an empty store is an empty array, not a 404")
	void an_empty_list_is_an_empty_array() throws Exception {
		this.mvc.perform(get("/api/customers").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	@DisplayName("UserEJB.java:64 — an invalid body is a 400, and nothing is stored")
	void an_invalid_registration_is_a_400() throws Exception {
		// The 25-character limit on userId (ejb-jar.xml / UserEJB) enforced by @Size on the
		// command. Per-field detail in the body is 4.7's job; here the contract is only the status
		// and the untouched store.
		this.mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
			.content(body("a".repeat(26), PASSWORD)))
			.andExpect(status().isBadRequest());

		assertThat(this.template.getCollection("customers").countDocuments()).isZero();
	}

	@Test
	@DisplayName("registered over the form, visible over the API — the view layer is not load-bearing")
	void a_form_registration_is_visible_over_the_api() throws Exception {
		this.mvc.perform(CustomerScreenTest.registration("ada").with(csrf())).andExpect(status().is3xxRedirection());

		this.mvc.perform(get("/api/customers").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].userId").value("ada"))
			.andExpect(jsonPath("$[0].contactInfo.email").value("ada@example.com"));
	}

	@Test
	@DisplayName("registered over the API, visible on the page — same service, other direction")
	void an_api_registration_is_visible_on_the_page() throws Exception {
		this.mvc.perform(registration("grace")).andExpect(status().isCreated());

		this.mvc.perform(get("/customers"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("grace")))
			.andExpect(content().string(containsString("Lovelace")));
	}

	static MockHttpServletRequestBuilder registration(String userId) {
		return post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body(userId, PASSWORD));
	}

	/** The JSON shape is the command's shape: the record's component names, nested as the CMP graph nests. */
	static String body(String userId, String password) {
		return """
				{
				  "userId": "%s",
				  "password": "%s",
				  "contactInfo": {
				    "givenName": "Ada",
				    "familyName": "Lovelace",
				    "telephone": "020 7946 0000",
				    "email": "ada@example.com",
				    "address": {
				      "streetName1": "1 Main St",
				      "streetName2": null,
				      "city": "London",
				      "state": "LDN",
				      "zipCode": "N1 1AA",
				      "country": "GB"
				    }
				  }
				}
				""".formatted(userId, password);
	}

}
