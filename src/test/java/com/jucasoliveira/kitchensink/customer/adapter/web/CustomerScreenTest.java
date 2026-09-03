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
