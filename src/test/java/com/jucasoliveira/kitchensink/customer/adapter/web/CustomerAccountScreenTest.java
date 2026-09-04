package com.jucasoliveira.kitchensink.customer.adapter.web;

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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 4.5 — {@code customer.screen}, the account a signed-on shopper sees.
 *
 * <h2>What this screen is, and is not</h2>
 *
 * <p>{@code screendefinitions_en_US.xml:128-135} bound {@code customer.screen} to
 * {@code /customer.jsp}: a read-only rendering of the shopper's own account —
 * {@code <c:out value="${customer.account.contactInfo...}"/>} throughout, with the profile shown in
 * {@code <waf:select editable="false">} dropdowns. {@code update_customer.screen} (:92-99) was its
 * writable twin over {@code edit_customer.jsp}. This template is the first plus the profile half of
 * the second, because {@code preferredLanguage} is the field this issue has to make settable; full
 * contact-info editing is not in #26 and is called out as such in the template's own comment.
 *
 * <p>It is emphatically not {@code /customers}, the registration screen and its table of everyone.
 * That table is the kitchensink twin's invention (see {@code CustomerScreenTest}); Pet Store had no
 * "list customers" screen at all, and {@link #the_screen_shows_only_the_signed_on_shopper()} keeps
 * the two from converging.
 *
 * <h2>Two absences worth asserting</h2>
 *
 * <p>{@code customer.jsp:120-145} rendered Credit Card Information from {@code CreditCardEJB}. That
 * bean is the half of 4.1 (#58) still open, so it is not in the aggregate and the block is absent.
 * {@code customer.jsp:186-196} rendered {@code bannerPreference}; the advice banner is T3 under
 * ADR-0006. Asserting an absence is how a slice keeps its edges visible: a screen that renders an
 * empty "Card Number" row is a different claim from one that never had the field.
 */
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerAccountScreenTest {

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
	@DisplayName("signon-config.xml:53 — customer.screen is protected; an anonymous request is sent to sign in")
	void the_screen_is_protected() throws Exception {
		// SignOnFilter.doFilter:142-147 forwarded to signon.screen rather than answering 403. The
		// chain redirects for the same reason: this is a page, and a shopper who lands on it
		// without a session should be asked to sign in, not told off.
		this.mvc.perform(get("/customers/me"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("customer.jsp:47-118 — the signed-on shopper's contact info renders, one row per CMP field")
	void the_screen_shows_the_contact_information() throws Exception {
		this.registration.register(command());

		this.mvc.perform(get("/customers/me").session(signIn()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Ada")))
			.andExpect(content().string(containsString("Lovelace")))
			.andExpect(content().string(containsString("1 Main St")))
			.andExpect(content().string(containsString("London")))
			.andExpect(content().string(containsString("LDN")))
			.andExpect(content().string(containsString("N1 1AA")))
			.andExpect(content().string(containsString("GB")))
			.andExpect(content().string(containsString("020 7946 0000")))
			.andExpect(content().string(containsString("ada@example.com")));
	}

	@Test
	@DisplayName("customer.jsp:148-163 — the profile form posts the two editable fields, with the stored language selected")
	void the_profile_form_carries_the_stored_preference() throws Exception {
		this.registration.register(command());
		this.registration.updateProfile(USER_ID, "ja_JP", "FISH");

		// The input names ARE the binding contract for CustomerController.updateProfile's
		// @RequestParam pair — a renamed select is a silently unbound parameter, so they are pinned
		// here the way CustomerScreenTest pins the registration form's names.
		this.mvc.perform(get("/customers/me").session(signIn()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"preferredLanguage\"")))
			.andExpect(content().string(containsString("name=\"favoriteCategory\"")))
			.andExpect(content().string(containsString("action=\"/customers/me/profile\"")))
			// th:action is what makes Thymeleaf emit the token; without it every save is a 403.
			.andExpect(content().string(containsString("name=\"_csrf\"")))
			.andExpect(content().string(containsString("value=\"ja_JP\" selected")));
	}

	@Test
	@Tag("parity")
	@DisplayName("customer.screen showed one account — this shopper's, never the whole store's")
	void the_screen_shows_only_the_signed_on_shopper() throws Exception {
		this.registration.register(command());
		this.registration.register(new RegisterCustomerCommand("grace", PASSWORD,
				new ContactInfo("Grace", "Hopper", "0800 000 000", "grace@example.com",
						new Address("3 Navy Way", null, "Arlington", "VA", "22204", "US"))));

		this.mvc.perform(get("/customers/me").session(signIn()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Lovelace")))
			.andExpect(content().string(not(containsString("Hopper"))))
			.andExpect(content().string(not(containsString("grace@example.com"))));
	}

	@Test
	@DisplayName("finding #1 — neither the password nor its hash reaches the account page")
	void the_credential_never_reaches_the_page() throws Exception {
		this.registration.register(command());

		this.mvc.perform(get("/customers/me").session(signIn()))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString(PASSWORD))))
			.andExpect(content().string(not(containsString("$2a$"))))
			.andExpect(content().string(not(containsString("passwordHash"))));
	}

	@Test
	@DisplayName("ADR-0006 — the credit card and advice-banner blocks of customer.jsp are absent, not empty")
	void the_unbuilt_blocks_are_absent() throws Exception {
		this.registration.register(command());

		// customer.jsp:120-145 (CreditCardEJB, open on #58) and :186-196 (the advice banner, T3).
		// An empty row would read as data that failed to load; nothing at all reads as scope.
		this.mvc.perform(get("/customers/me").session(signIn()))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("Card Number"))))
			.andExpect(content().string(not(containsString("Card Type"))))
			.andExpect(content().string(not(containsString("Credit Card"))))
			.andExpect(content().string(not(containsString("banner feature"))));
	}

	@Test
	@DisplayName("the screen goes through the same MessageSource as the rest: ja_JP renders it in Japanese")
	void the_screen_is_localised() throws Exception {
		this.registration.register(command());
		MockHttpSession session = signIn();

		// ja/customer.jsp:48,49 — 顧客情報 and 連絡先. The contact values stay Latin because they are
		// the shopper's own data, which no catalogue localises.
		this.mvc.perform(get("/customers/me").session(session).param("locale", "ja_JP"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("顧客情報")))
			.andExpect(content().string(containsString("連絡先")))
			.andExpect(content().string(containsString("Lovelace")))
			.andExpect(content().string(not(containsString("Contact Information"))));
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
