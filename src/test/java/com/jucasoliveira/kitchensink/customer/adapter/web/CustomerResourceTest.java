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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

	/**
	 * Issue 4.2 (#59) took {@code GET /api/customers} out of the permit list: the resource
	 * returns the whole aggregate — email, telephone and full address — so the chain now turns
	 * an anonymous read away, while {@code POST} to the same path stays public because creating
	 * an account cannot require a session ({@code createuser.do} was never in
	 * {@code signon-config.xml}). That is why the permit is written per-method rather than
	 * per-path, and why the reads below authenticate and the registrations do not. The identity
	 * is irrelevant — the listing is not per-user — so this is a synthetic principal, which
	 * keeps these tests about the resource rather than about sign-on.
	 * {@code SignOnConfigParityTest} is where the rule itself is pinned.
	 */
	static final String READER = "reader";


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

		this.mvc.perform(get("/api/customers").with(user(READER)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[*].userId").value(org.hamcrest.Matchers.containsInAnyOrder("ada", "grace")))
			.andExpect(content().string(not(containsString("passwordHash"))));
	}

	@Test
	@DisplayName("GET /api/customers on an empty store is an empty array, not a 404")
	void an_empty_list_is_an_empty_array() throws Exception {
		this.mvc.perform(get("/api/customers").with(user(READER)).accept(MediaType.APPLICATION_JSON))
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
	@DisplayName("Issue 4.4 — a duplicate user id is a 409 problem document, not a 201 and not a 500")
	void a_duplicate_registration_is_a_409() throws Exception {
		this.mvc.perform(registration("ada")).andExpect(status().isCreated());

		this.mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
			.content(body("ada", "totally-different")))
			.andExpect(status().isConflict())
			// The class-level produces=application/json could have pinned the error body to plain
			// JSON; RFC 9457 is what CatalogResource's ResponseStatusException already emits, and a
			// client should not have to know which resource it is talking to to parse a failure.
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.title").value("Duplicate Account"))
			.andExpect(jsonPath("$.detail").value(containsString("in use")))
			.andExpect(jsonPath("$.userId").value("ada"))
			// Finding #1 holds on the failure path too: a 409 is the one response where it would be
			// tempting to echo the submitted credential back as "what you sent".
			.andExpect(content().string(not(containsString("totally-different"))))
			.andExpect(content().string(not(containsString("$2a$"))));

		// Same rule as the screen, other channel, and the same thing at stake: not "how many
		// documents" but "whose account".
		this.mvc.perform(get("/api/customers").with(user(READER)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].contactInfo.email").value("ada@example.com"));
	}

	@Test
	@DisplayName("the rule is the service's, not the channel's: form first, API second, still a 409")
	void a_duplicate_across_the_two_channels_is_still_rejected() throws Exception {
		// The acceptance criterion of 1.9 that this class exists to defend — "both channels go
		// through the same application service" — applied to the rule rather than to the happy
		// path. A duplicate check living in either web adapter would pass every test above and
		// fail this one.
		this.mvc.perform(CustomerScreenTest.registration("ada").with(csrf())).andExpect(status().is3xxRedirection());

		this.mvc.perform(registration("ada")).andExpect(status().isConflict());

		assertThat(this.template.getCollection("customers").countDocuments()).isEqualTo(1);
	}

	@Test
	@DisplayName("registered over the form, visible over the API — the view layer is not load-bearing")
	void a_form_registration_is_visible_over_the_api() throws Exception {
		this.mvc.perform(CustomerScreenTest.registration("ada").with(csrf())).andExpect(status().is3xxRedirection());

		this.mvc.perform(get("/api/customers").with(user(READER)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].userId").value("ada"))
			.andExpect(jsonPath("$[0].contactInfo.email").value("ada@example.com"));
	}

	@Test
	@DisplayName("registered over the API, visible on the page — same service, other direction")
	void an_api_registration_is_visible_on_the_page() throws Exception {
		this.mvc.perform(registration("grace")).andExpect(status().isCreated());

		this.mvc.perform(get("/customers").with(user(READER)))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("grace")))
			.andExpect(content().string(containsString("Lovelace")));
	}

	@Test
	@DisplayName("Issue 4.7 — GET /api/customers/{id} returns the aggregate, and never the credential")
	void a_customer_is_readable_by_id() throws Exception {
		this.mvc.perform(registration("ada")).andExpect(status().isCreated());

		this.mvc.perform(get("/api/customers/ada").with(user(READER)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId").value("ada"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.contactInfo.familyName").value("Lovelace"))
			.andExpect(jsonPath("$.contactInfo.address.city").value("London"))
			// ProfileLocalHome.java:44-47, through the API this time.
			.andExpect(jsonPath("$.profile.preferredLanguage").value("en_US"))
			.andExpect(content().string(not(containsString("passwordHash"))))
			.andExpect(content().string(not(containsString(PASSWORD))));
	}

	@Test
	@DisplayName("Issue 4.7 — an unknown id is a 404 ProblemDetail, not an empty 200 and not a 500")
	void an_unknown_id_is_a_404() throws Exception {
		// CustomerEJB's home declared only findByPrimaryKey and a miss was a FinderException the
		// caller had to catch; the port made it Optional.empty() and this is where the adapter
		// decides what an empty Optional means over HTTP. Deliberately the resource's decision and
		// not the service's — CustomerRegistration.byUserId stays store-shaped.
		this.mvc.perform(get("/api/customers/nobody").with(user(READER)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Unknown Customer"))
			.andExpect(jsonPath("$.userId").value("nobody"));
	}

	@Test
	@DisplayName("Issue 4.7 — a validation failure names every field that failed, not just the status")
	void an_invalid_registration_reports_each_field() throws Exception {
		// The acceptance criterion is "400 with per-field detail". Without the handler this is a
		// bare 400 and the caller has to guess which of RegisterCustomerCommand's constraints it
		// broke, while the form channel renders them one by one (CustomerScreenTest). Same rules,
		// same service, so the API should not be the poorer channel.
		this.mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Validation Failed"))
			.andExpect(jsonPath("$.errors.userId").exists())
			.andExpect(jsonPath("$.errors.password").exists())
			.andExpect(jsonPath("$.errors.contactInfo").exists());

		assertThat(this.template.getCollection("customers").countDocuments()).isZero();
	}

	@Test
	@DisplayName("Issue 4.7 — a nested violation is reported at its path, not at the root")
	void a_nested_violation_keeps_its_path() throws Exception {
		// @Valid on ContactInfo cascades into Address, so a blank city is "contactInfo.address.city"
		// and not "contactInfo". That path is what lets a client highlight the field, and it is the
		// same binding path the Thymeleaf form uses for th:field — one contract, two channels.
		this.mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
				.content(body("ada", PASSWORD).replace("\"city\": \"London\"", "\"city\": \"\"")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors['contactInfo.address.city']").exists());
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
