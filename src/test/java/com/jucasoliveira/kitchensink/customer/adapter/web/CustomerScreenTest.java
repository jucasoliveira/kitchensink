package com.jucasoliveira.kitchensink.customer.adapter.web;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 1.9 — the Thymeleaf half of the walking skeleton: one page with a registration form and
 * a table of who has registered, the kitchensink {@code index.xhtml} shape (ADR-0006, "the direct
 * twin").
 *
 * <p>Legacy anchor: {@code docroot/create_customer.jsp} posted to {@code createcustomer.do}
 * ({@code WEB-INF/mappings.xml:90-93}), where {@code CustomerHTMLAction.extractContactInfo}
 * ({@code CustomerHTMLAction.java:182-253}) pulled the {@code *_a} parameters out of the request
 * by hand and collected missing ones into a {@code MissingFormDataException}. The user id and
 * password came from a separate screen, {@code signon.jsp:147-171} → {@code createuser.do} →
 * {@code CreateUserHTMLAction.java:76-78}. Here the two screens are one form, bound to the same
 * {@code RegisterCustomerCommand} the application service validates, so the "missing fields" list
 * is Jakarta Validation's rather than a hand-rolled {@code ArrayList}.
 *
 * <p>The legacy had no "list customers" screen at all — {@code customer.screen} showed the signed-on
 * user's own account. The table is the kitchensink twin's, not Pet Store's, and it is what makes
 * "register-and-list" demonstrable in a browser.
 *
 * <p>Every request here is anonymous: registration is the one thing you do before you have an
 * account, and {@code createuser.do} / {@code createcustomer.do} are absent from
 * {@code signon-config.xml}'s protected list for the same reason.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerScreenTest {

	static final String PASSWORD = "s3cret";

	@Autowired
	MockMvc mvc;

	@Autowired
	MongoTemplate template;

	@Autowired
	PasswordEncoder encoder;

	@BeforeEach
	void reset() {
		this.template.dropCollection("customers");
	}

	@Test
	@DisplayName("create_customer.jsp — the form is served to an anonymous visitor, one input per CMP field")
	void the_registration_form_renders() throws Exception {
		// The input names ARE the binding contract: Spring binds "contactInfo.address.city" onto
		// the nested record constructor, so a renamed input is a silently null field. Pinning the
		// names here is what keeps the POST test below honest.
		this.mvc.perform(get("/customers"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"userId\"")))
			.andExpect(content().string(containsString("name=\"password\"")))
			.andExpect(content().string(containsString("name=\"contactInfo.givenName\"")))
			.andExpect(content().string(containsString("name=\"contactInfo.familyName\"")))
			.andExpect(content().string(containsString("name=\"contactInfo.telephone\"")))
			.andExpect(content().string(containsString("name=\"contactInfo.email\"")))
			.andExpect(content().string(containsString("name=\"contactInfo.address.streetName1\"")))
			.andExpect(content().string(containsString("name=\"contactInfo.address.city\"")))
			.andExpect(content().string(containsString("name=\"contactInfo.address.state\"")))
			.andExpect(content().string(containsString("name=\"contactInfo.address.zipCode\"")));
	}

	@Test
	@DisplayName("a valid form posts, redirects (PRG), and the new customer is in the list")
	void a_valid_registration_posts_and_is_listed() throws Exception {
		// The legacy forwarded to whatever screen the shopper had been on (CreateUserFlowHandler:67,
		// SignOnFilter.ORIGINAL_URL). The skeleton has one screen, so it redirects back to it.
		this.mvc.perform(registration("ada").with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/customers"));

		this.mvc.perform(get("/customers"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("ada")))
			.andExpect(content().string(containsString("Lovelace")))
			.andExpect(content().string(containsString("London")));

		assertThat(this.template.getCollection("customers").countDocuments()).isEqualTo(1);
	}

	@Test
	@DisplayName("CustomerHTMLAction.java:185-190 — a missing last name re-renders the form with the violation, and stores nothing")
	void a_missing_field_is_reported_on_the_form() throws Exception {
		// Legacy: missingFields.add("Last Name") → MissingFormDataException → same screen again.
		// Here the same rule is @NotBlank on ContactInfo.familyName, reported by BindingResult.
		this.mvc.perform(registration("ada", "").with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("must not be blank")))
			// The re-rendered form keeps what was typed — except the password, which a password
			// input never echoes.
			.andExpect(content().string(containsString("ada@example.com")))
			.andExpect(content().string(not(containsString(PASSWORD))));

		assertThat(this.template.getCollection("customers").countDocuments()).isZero();
	}

	@Test
	@Tag("parity")
	@DisplayName("mappings.xml:109 — a second registration on a taken user id gets duplicate_account.screen, not the first account")
	void a_duplicate_registration_is_sent_to_the_duplicate_account_screen() throws Exception {
		// Legacy: DuplicateAccountException was not handled by the action at all — mappings.xml:109
		// is an <exception-mapping>, so the WAF caught it centrally and rendered
		// duplicate_account.screen (screendefinitions_en_US.xml:170-176). @ExceptionHandler is the
		// same idea at controller scope: the POST handler stays free of the failure path.
		this.mvc.perform(registration("ada").with(csrf())).andExpect(status().is3xxRedirection());

		this.mvc.perform(registration("ada").with(csrf()))
			// The one deliberate departure: the legacy screen was a 200, because in 2002 every screen
			// was. Same departure, same reasoning as the catalog's 404 for an unknown id — see
			// CatalogScreenTest, "not a status code you can build a client on".
			.andExpect(status().isConflict())
			// duplicate_account.jsp:44-48, both halves: the heading and the instruction.
			.andExpect(content().string(containsString("User Creation Error!")))
			.andExpect(content().string(containsString("in use")))
			// Not the registration screen re-rendered: a shopper who sees this has left the form.
			.andExpect(content().string(not(containsString("name=\"userId\""))));

		assertThat(this.template.getCollection("customers").countDocuments()).isEqualTo(1);
	}

	@Test
	@Tag("parity")
	@DisplayName("the first account survives a duplicate registration whole — contact info and credential both")
	void a_duplicate_registration_does_not_take_over_the_first_account() throws Exception {
		// This is the test with teeth. Before #25 the second POST answered 3xx and MongoRepository
		// .save() upserted on the @Id, so an anonymous visitor could take over any account whose
		// user id they could guess — same document, their password. Counting documents does not
		// catch that; only reading the survivor back does.
		this.mvc.perform(registration("ada").with(csrf())).andExpect(status().is3xxRedirection());

		this.mvc
			.perform(post("/customers").contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.param("userId", "ada")
				.param("password", "totally-different")
				.param("contactInfo.givenName", "Mallory")
				.param("contactInfo.familyName", "Attacker")
				.param("contactInfo.telephone", "029 2018 0000")
				.param("contactInfo.email", "impostor@example.com")
				.param("contactInfo.address.streetName1", "2 Other St")
				.param("contactInfo.address.streetName2", "")
				.param("contactInfo.address.city", "Cardiff")
				.param("contactInfo.address.state", "CDF")
				.param("contactInfo.address.zipCode", "CF10 1AA")
				.param("contactInfo.address.country", "GB")
				.with(csrf()))
			.andExpect(status().isConflict());

		this.mvc.perform(get("/customers"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Lovelace")))
			.andExpect(content().string(not(containsString("Mallory"))))
			.andExpect(content().string(not(containsString("Attacker"))));

		// And the credential is still the first registration's, which is the half the page cannot
		// show: SignOnTest proves a stored hash authenticates, so a takeover here would be invisible
		// on screen and total at the sign-on page.
		Document raw = this.template.getCollection("customers").find().first();
		assertThat(raw).isNotNull();
		assertThat(this.encoder.matches(PASSWORD, raw.getString("passwordHash"))).isTrue();
		assertThat(this.encoder.matches("totally-different", raw.getString("passwordHash"))).isFalse();
	}

	@Test
	@DisplayName("finding #1 — neither the password nor its hash ever appears in a rendered page")
	void the_credential_never_reaches_the_page() throws Exception {
		this.mvc.perform(registration("ada").with(csrf())).andExpect(status().is3xxRedirection());

		this.mvc.perform(get("/customers"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString(PASSWORD))))
			.andExpect(content().string(not(containsString("$2a$"))))
			.andExpect(content().string(not(containsString("passwordHash"))));
	}

	@Test
	@DisplayName("the form is behind the security filter chain: a post without a CSRF token is refused")
	void a_post_without_a_csrf_token_is_refused() throws Exception {
		// SignOnFilter had no CSRF protection at all (it was 2002). This is the one place the
		// skeleton is stricter than the legacy on purpose, and the refusal proves the chain of
		// Issue 1.8 is in front of the form, not beside it.
		this.mvc.perform(registration("ada")).andExpect(status().isForbidden());

		assertThat(this.template.getCollection("customers").countDocuments()).isZero();
	}

	/** One parameter per input of {@code create_customer.jsp}, minus credit card and profile (T3 / 4.5). */
	static MockHttpServletRequestBuilder registration(String userId) {
		return registration(userId, "Lovelace");
	}

	/**
	 * {@code param()} appends rather than replaces — a second value for the same name binds as
	 * "Lovelace," and is not blank — so the one field the tests vary is a parameter, not an override.
	 */
	static MockHttpServletRequestBuilder registration(String userId, String familyName) {
		return post("/customers").contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("userId", userId)
			.param("password", PASSWORD)
			.param("contactInfo.givenName", "Ada")
			.param("contactInfo.familyName", familyName)
			.param("contactInfo.telephone", "020 7946 0000")
			.param("contactInfo.email", "ada@example.com")
			.param("contactInfo.address.streetName1", "1 Main St")
			.param("contactInfo.address.streetName2", "")
			.param("contactInfo.address.city", "London")
			.param("contactInfo.address.state", "LDN")
			.param("contactInfo.address.zipCode", "N1 1AA")
			.param("contactInfo.address.country", "GB");
	}

}
