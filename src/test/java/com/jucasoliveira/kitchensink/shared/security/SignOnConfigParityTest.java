package com.jucasoliveira.kitchensink.shared.security;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import com.jucasoliveira.kitchensink.customer.application.CustomerRegistration;
import com.jucasoliveira.kitchensink.customer.application.RegisterCustomerCommand;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 4.2 — the whole of {@code signon-config.xml}, not the one URL issue 1.8 carried.
 *
 * <p>Legacy anchor: {@code components/signon/.../web/SignOnFilter.java}, mapped to {@code /*} at
 * {@code petstore/src/docroot/WEB-INF/web.xml:75-78}, reading its protected set from
 * {@code apps/petstore/src/docroot/WEB-INF/signon-config.xml}. Finding #3 claims that filter "maps
 * cleanly onto a Spring Security filter chain"; {@code SignOnTest} checked the claim for one URL,
 * and this class checks it for the file.
 *
 * <h2>The four patterns, and what became of them</h2>
 *
 * <table>
 * <tr><th>{@code signon-config.xml}</th><th>here</th></tr>
 * <tr><td>{@code customer.screen} (:53)</td><td>{@code GET /customers/me}</td></tr>
 * <tr><td>{@code customer.do} (:61)</td><td>{@code POST /customers/me/profile}</td></tr>
 * <tr><td>{@code enter_order_information.screen} (:69)</td><td>nothing — checkout is T3, deferred
 * under ADR-0006</td></tr>
 * <tr><td>{@code signon_welcome.screen} (:77)</td><td>nothing — the post-login landing here is
 * {@code /}, the public store front (commit {@code d2ab573})</td></tr>
 * </table>
 *
 * <h2>The one deliberate inversion</h2>
 *
 * <p>{@code SignOnFilter.doFilter:141} matched with {@code urlPattern.equals(targetURL)} — exact
 * string equality, no wildcards and no prefixes — so {@code signon-config.xml} was a <em>deny
 * list</em> and every URL absent from it was public. The cost of that default is visible in the
 * legacy's own configuration: {@code mappings.xml} declares seven action URLs and exactly one of
 * them, {@code customer.do}, appears in {@code signon-config.xml}. {@code enter_order_information
 * .screen}, the checkout <em>form</em>, is protected; {@code order.do}, which <em>places the
 * order</em>, is not. Neither is {@code cart.do}.
 *
 * <p>This chain inverts that default: {@code anyRequest().authenticated()} with a permit list, so a
 * URL nobody thought about fails closed. {@link #an_unlisted_url_is_protected_here_and_was_public_there()}
 * is where that divergence is written down rather than left as a happy accident, and it is the
 * honest answer to "did you achieve parity?" — no, deliberately, and here is the legacy URL that
 * explains why.
 *
 * <p>Tagged per-method rather than per-class: the parity rules are the protected set and the public
 * set, while the two PII regressions at the bottom are ordinary correctness and should fail the
 * everyday build.
 */
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignOnConfigParityTest {

	static final String USER_ID = "shopper";

	static final String PASSWORD = "s3cret";

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
	@Tag("parity")
	@DisplayName("signon-config.xml:53,61 — both patterns that have an equivalent send an anonymous request to sign in")
	void the_protected_set_matches_signon_config() throws Exception {
		// SignOnFilter.doFilter:142-147 stashed the target in the session and FORWARDED to
		// signon.screen; the chain redirects instead. Same outcome for the shopper — asked to sign
		// in rather than told off — reached by a 302 rather than a server-side forward, which is
		// the one mechanical difference between a 2003 filter and this chain.
		this.mvc.perform(get("/customers/me"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));

		// customer.do was the action behind the same screen (mappings.xml:95). Its equivalent is a
		// POST, so CSRF has to be satisfied first or a 403 would mask the authorization result and
		// this would pass for the wrong reason.
		this.mvc.perform(post("/customers/me/profile").with(csrf()).param("preferredLanguage", "ja_JP"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@Tag("parity")
	@DisplayName("createuser.do and createcustomer.do were NOT in signon-config.xml — registering stays public")
	void creating_an_account_needs_no_session() throws Exception {
		// The two URLs that created a customer (mappings.xml:86,90) are absent from the protected
		// set, and deliberately so: a shopper cannot sign in to get an account. Protecting the
		// registration form is the single most likely way to break this slice, so it is pinned.
		this.mvc.perform(get("/customers"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"userId\"")));

		// The REST twin of the same rule (#61). A 400 from bean validation proves the request
		// reached the handler; a 302 would mean the chain turned it away first.
		this.mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@Tag("parity")
	@DisplayName("the store front and the catalogue were never in signon-config.xml, and are still public")
	void browsing_needs_no_session() throws Exception {
		// "/" is HomeController's redirect to /catalog (d2ab573), so a 302 is the handler and not
		// the chain — the URL under test is public either way, and naming the target is what tells
		// the two apart. A regression here would send it to /login instead.
		this.mvc.perform(get("/")).andExpect(redirectedUrl("/catalog"));
		this.mvc.perform(get("/catalog")).andExpect(status().isOk());
		this.mvc.perform(get("/api/catalog/categories")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("finding #3, inverted — a URL in neither list is protected here; the legacy left order.do public")
	void an_unlisted_url_is_protected_here_and_was_public_there() throws Exception {
		// /orders does not exist in this application and is not meant to: what is under test is the
		// DEFAULT, not a handler. Under anyRequest().authenticated() an unbuilt or forgotten URL is
		// closed. Under signon-config.xml's exact-match deny list the real order.do — the URL that
		// placed the order — was open, because nobody added it to the file.
		//
		// This is the assertion to change if the chain ever moves back to a protect-list, and the
		// comment above is the reason not to.
		this.mvc.perform(get("/orders"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("the member list is a signed-on view: an anonymous visitor gets the form and nobody's details")
	void the_member_list_is_not_public() throws Exception {
		// /customers serves two things at one URL — create_customer.screen's form, which must stay
		// public, and the kitchensink twin's member table, which lists every registered shopper's
		// id, name and city. Pet Store had no such screen, so there is no parity argument for
		// showing it to an anonymous request; CustomerController populates the model only when
		// there is a Principal.
		this.registration.register(command());

		this.mvc.perform(get("/customers"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"userId\"")))
			.andExpect(content().string(not(containsString("Lovelace"))))
			.andExpect(content().string(not(containsString("London"))));
	}

	@Test
	@DisplayName("the member list renders once the visitor has signed on")
	void the_member_list_is_visible_to_a_signed_on_visitor() throws Exception {
		// The other half of the test above: proving the rows are hidden is only meaningful
		// alongside proving they are still there, or an empty table would pass both ways.
		this.registration.register(command());

		this.mvc.perform(get("/customers").session(signIn()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Lovelace")))
			.andExpect(content().string(containsString("London")));
	}

	@Test
	@DisplayName("GET /api/customers is the same listing in JSON, and is protected the same way")
	void the_rest_listing_is_not_public() throws Exception {
		// The HTML table hides its rows in the model; the REST resource returns the whole
		// aggregate — email, telephone and full address included — so it is turned away by the
		// chain instead. POST to the same path stays public, which is why the permit is written
		// per-method rather than per-path.
		this.registration.register(command());

		this.mvc.perform(get("/api/customers"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	private MockHttpSession signIn() throws Exception {
		return (MockHttpSession) this.mvc.perform(formLogin().user(USER_ID).password(PASSWORD))
			.andExpect(authenticated().withUsername(USER_ID))
			.andReturn()
			.getRequest()
			.getSession(false);
	}

	private static RegisterCustomerCommand command() {
		return new RegisterCustomerCommand(USER_ID, PASSWORD, new ContactInfo("Ada", "Lovelace", "020 7946 0000",
				"ada@example.com", new Address("1 Main St", null, "London", "LDN", "N1 1AA", "GB")));
	}

}
