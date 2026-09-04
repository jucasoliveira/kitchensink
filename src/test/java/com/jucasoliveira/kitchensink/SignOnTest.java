package com.jucasoliveira.kitchensink;

import com.jucasoliveira.kitchensink.customer.application.CustomerRegistration;
import com.jucasoliveira.kitchensink.customer.application.RegisterCustomerCommand;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import jakarta.validation.ConstraintViolationException;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 1.8 — Spring Security replaces {@code SignOnFilter}; BCrypt replaces {@code UserEJB:88}.
 *
 * <p>The legacy sign-on was a hand-written servlet filter mapped to {@code /*}
 * ({@code petstore/src/docroot/WEB-INF/web.xml:75-78}) that read its protected URLs from
 * {@code signon-config.xml}, kept a {@code j_signon} boolean in the session, and on
 * {@code j_signon_check} called {@code SignOnEJB.authenticate}, which did
 * {@code findByPrimaryKey(userName)} then {@code user.matchPassword(password)} — a plaintext
 * {@code equals} ({@code SignOnEJB.java:71-78}, {@code UserEJB.java:88}). Finding #3 says that
 * "maps cleanly onto Spring Security filter chain"; this class is where the claim is checked.
 *
 * <p>The mapping, piece by piece:
 * <ul>
 * <li>{@code signon-config.xml:50-55} protects {@code customer.screen} → {@code /customer} is the one
 * protected URL this issue carried; 4.2 does the rest of the file, in SignOnConfigParityTest;</li>
 * <li>{@code SignOnFilter.doFilter:142-147} forwards an anonymous request to the sign-on page →
 * the chain redirects to {@code /login};</li>
 * <li>{@code SignOnEJB.authenticate} → a {@code UserDetailsService} over the customer aggregate,
 * with {@code DaoAuthenticationProvider} doing the comparison against a BCrypt hash.</li>
 * </ul>
 *
 * <p>Boots the real context under the default ({@code mongo}) profile, as the round-trip test
 * does, because the point is that the chain, the user lookup, the hashing and the store are the
 * <em>production</em> wiring — nothing here is stubbed.
 *
 * <p>Issue 2.2: three methods below carry {@code @Tag("parity")} rather than the class, so the
 * rest keep running - and counting toward domain/application coverage - in the {@code build} job.
 * {@link #an_unknown_user_cannot_sign_in()} and {@link #a_registered_customer_can_sign_in()} are
 * the sign-on success/failure half of the in-scope business rules;
 * {@link #the_password_never_reaches_the_store()} is the BCrypt deviation (finding #1) the
 * plaintext {@code UserEJB.matchPassword} equality could never have shown.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignOnTest {

	/** The legacy seed's shopper ({@code Populate-UTF8.xml:79-80}), with a password that is not "j2ee". */
	static final String USER_ID = "shopper";

	static final String PASSWORD = "s3cret-and-25-chars-max";

	/** Modular-crypt BCrypt: revision, two-digit cost, 22 chars of salt + 31 of digest. */
	static final String BCRYPT = "\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}";

	@Autowired
	MockMvc mvc;

	@Autowired
	CustomerRegistration registration;

	@Autowired
	MongoTemplate template;

	@BeforeEach
	void reset() {
		this.template.dropCollection("customers");
	}

	@Test
	@DisplayName("signon-config.xml:53 — /customers/me is protected, and an anonymous request is sent to sign in")
	void the_protected_url_redirects_an_anonymous_request_to_login() throws Exception {
		// Not a 401 and not a 404: SignOnFilter:145 forwarded to signon.screen, and the chain
		// redirects to its login page the same way, before any handler is looked up.
		//
		// This used to ask for "/customer", which no controller maps — CustomerController is
		// @RequestMapping("/customers"). It passed because authorization runs before handler
		// lookup, so ANY unmapped URL redirects under anyRequest().authenticated(): it was
		// asserting the default rather than the legacy rule it names. Issue 4.2 points it at the
		// screen customer.screen actually became. The whole signon-config.xml set is walked by
		// SignOnConfigParityTest.
		this.mvc.perform(get("/customers/me"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@Tag("parity")
	@DisplayName("SignOnEJB.java:75-77 — an unknown user fails to sign in, and is told no more than that")
	void an_unknown_user_cannot_sign_in() throws Exception {
		// The legacy's FinderException became "return false"; here UsernameNotFoundException is
		// folded into BadCredentials, so the response does not reveal which of the two it was.
		this.mvc.perform(formLogin().user("nobody").password(PASSWORD))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	@Tag("parity")
	@DisplayName("SignOnEJB.java:71-74 — a registered customer signs in with the password they registered")
	void a_registered_customer_can_sign_in() throws Exception {
		register();

		this.mvc.perform(formLogin().user(USER_ID).password(PASSWORD))
			.andExpect(authenticated().withUsername(USER_ID))
			.andExpect(redirectedUrl("/"));
	}

	@Test
	@DisplayName("UserEJB.java:88 — the wrong password is refused, by a hash comparison rather than equals")
	void the_wrong_password_is_refused() throws Exception {
		register();

		this.mvc.perform(formLogin().user(USER_ID).password("j2ee"))
			.andExpect(unauthenticated())
			.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	@Tag("parity")
	@DisplayName("finding #1 — what reaches the database is a BCrypt hash, and the password is nowhere in the document")
	void the_password_never_reaches_the_store() {
		register();

		Document raw = this.template.getCollection("customers").find().first();
		assertThat(raw).isNotNull();
		assertThat(raw.getString("passwordHash")).matches(BCRYPT);
		// Belt and braces: not under any other key either, and not inside a nested subdocument.
		assertThat(raw.toJson()).doesNotContain(PASSWORD);
		assertThat(raw).doesNotContainKey("password");
	}

	@Test
	@DisplayName("the application service is the validation backstop: an invalid registration never reaches the store")
	void an_invalid_registration_is_rejected_before_it_is_stored() {
		// Issue 1.9's form validates first and renders the violations; this is what stands behind
		// it, so that a caller which skips the form (the REST resource of 4.7, a test, a bug)
		// still cannot store a 26-character user id. UserEJB.java:64 threw CreateException here.
		RegisterCustomerCommand tooLong = new RegisterCustomerCommand("a".repeat(26), PASSWORD, contact());

		assertThatExceptionOfType(ConstraintViolationException.class)
			.isThrownBy(() -> this.registration.register(tooLong));
		assertThat(this.template.getCollection("customers").countDocuments()).isZero();
	}

	private void register() {
		this.registration.register(new RegisterCustomerCommand(USER_ID, PASSWORD, contact()));
	}

	static ContactInfo contact() {
		return new ContactInfo("Ada", "Lovelace", "020 7946 0000", "ada@example.com",
				new Address("1 Main St", null, "London", "LDN", "N1 1AA", "GB"));
	}

}
