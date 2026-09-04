package com.jucasoliveira.kitchensink.customer.adapter.web;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import com.jucasoliveira.kitchensink.customer.application.CustomerRegistration;
import com.jucasoliveira.kitchensink.customer.application.RegisterCustomerCommand;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import com.jucasoliveira.kitchensink.customer.domain.Customer;
import com.jucasoliveira.kitchensink.customer.domain.Profile;
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

import static org.assertj.core.api.Assertions.assertThat;
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
 * Issue 4.5 — {@code ProfileEJB.preferredLanguage}, honoured.
 *
 * <h2>Two legacy write points, one field</h2>
 *
 * <p>{@code preferredLanguage} was a CMP field of {@code ProfileEJB} ({@code ProfileEJB.java:52-53}),
 * created with the default {@code "en_US"} ({@code ProfileLocalHome.java:44}) by
 * {@code CustomerEJB.ejbPostCreate:80-84} — every account started English whatever the browser said.
 * Two places then moved the session locale to match it:
 *
 * <ul>
 * <li>{@code SignOnNotifier.java:138-140} — on sign-on, read the stored profile and set the session
 * locale from it. That is {@code PreferredLanguageSuccessHandler}.</li>
 * <li>{@code CustomerEJBAction.java:138-143} — on saving the profile, write the fields <em>and</em>
 * set the locale, in the same method. That is {@code CustomerController.updateProfile}.</li>
 * </ul>
 *
 * <p>The pair is what makes the preference stick rather than merely exist:
 * {@link #the_preference_survives_a_sign_out_and_back_in()} is the round trip through the store, and
 * it is the assertion that fails if either half is dropped.
 *
 * <h2>Why the two booleans are not settable here</h2>
 *
 * <p>{@code ProfileEJB} had four fields; {@code myListPreference} and {@code bannerPreference}
 * ({@code ProfileEJB.java:58-62}) gate MyList and the advice banner, both T3 under ADR-0006 and
 * deliberately unbuilt. They are carried as data — ADR-0006 keeps the locale-scoped data model — but
 * {@code updateProfile} takes two strings rather than a whole {@code Profile}, so no form can set a
 * flag for a feature that does not exist. {@link #the_unbuilt_preferences_are_carried_not_edited()}
 * is what stops that being an accident of the current template.
 */
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PreferredLanguageTest {

	static final String USER_ID = "shopper";

	static final String PASSWORD = "s3cret";

	static final String ANGELFISH_EN = "Angelfish";

	static final String ANGELFISH_JA = "エンゼルフィッシュ";

	static final String FISH = "/catalog/categories/FISH";

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
	@DisplayName("ProfileLocalHome.java:44 — a new account starts en_US, whatever the browser asked for")
	void a_new_account_starts_in_english() {
		// CustomerEJB.ejbPostCreate:80-84 passed the DefaultPreferredLanguage constant, not
		// anything derived from the request. Registering through the service rather than asserting
		// on Profile.DEFAULT proves the default survives the write path, not just the constant.
		Customer registered = this.registration.register(command());

		assertThat(registered.profile().preferredLanguage()).isEqualTo("en_US");
		assertThat(this.registration.byUserId(USER_ID).orElseThrow().profile().preferredLanguage())
			.isEqualTo("en_US");
	}

	@Test
	@Tag("parity")
	@DisplayName("SignOnNotifier.java:138-140 — signing on sets the session locale from the stored profile")
	void signing_on_adopts_the_stored_preference() throws Exception {
		this.registration.register(command());
		this.registration.updateProfile(USER_ID, "ja_JP", null);

		// The shopper never touches a flag: the only thing that happened is a sign-on, and the very
		// next page is Japanese. Before PreferredLanguageSuccessHandler the profile was a stored
		// field nothing read, which is indistinguishable from this on any test that only asserts
		// the field's value.
		MockHttpSession session = signIn();

		this.mvc.perform(get(FISH).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_JA)))
			.andExpect(content().string(not(containsString(ANGELFISH_EN))));
	}

	@Test
	@DisplayName("a signed-on shopper whose profile is en_US still gets English, header notwithstanding")
	void signing_on_with_the_default_preference_stays_english() throws Exception {
		// The other side of the handler: it must set the locale, not merely fail to break it. A
		// Japanese Accept-Language on the request would flip an AcceptHeaderLocaleResolver, and the
		// stored preference is what wins instead.
		this.registration.register(command());
		MockHttpSession session = signIn();

		this.mvc.perform(get(FISH).session(session).locale(java.util.Locale.JAPAN))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_EN)))
			.andExpect(content().string(not(containsString(ANGELFISH_JA))));
	}

	@Test
	@Tag("parity")
	@DisplayName("CustomerEJBAction.java:142-143 — saving the profile switches the current session too")
	void saving_the_profile_switches_the_session_immediately() throws Exception {
		this.registration.register(command());
		MockHttpSession session = signIn();

		this.mvc.perform(post("/customers/me/profile").session(session)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("preferredLanguage", "ja_JP")
			.with(csrf())).andExpect(redirectedUrl("/customers/me"));

		// Not "on the next sign-on": the legacy set machine.setAttribute("locale", ...) in the same
		// method as the field writes, so the screen after the save was already translated.
		this.mvc.perform(get(FISH).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_JA)));
	}

	@Test
	@Tag("parity")
	@DisplayName("the preference is stored, not just sessioned: it survives a sign-out and back in")
	void the_preference_survives_a_sign_out_and_back_in() throws Exception {
		this.registration.register(command());
		MockHttpSession first = signIn();

		this.mvc.perform(post("/customers/me/profile").session(first)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("preferredLanguage", "ja_JP")
			.with(csrf())).andExpect(status().is3xxRedirection());

		// A brand-new session — the equivalent of closing the browser. If updateProfile had only
		// called LocaleResolver.setLocale and skipped the repository, every test above would still
		// pass and this one would fail, which is why it is here.
		MockHttpSession second = signIn();

		this.mvc.perform(get(FISH).session(second))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_JA)))
			.andExpect(content().string(not(containsString(ANGELFISH_EN))));
	}

	@Test
	@DisplayName("ADR-0006 — myListPreference and bannerPreference are carried through a save, never set by it")
	void the_unbuilt_preferences_are_carried_not_edited() throws Exception {
		this.registration.register(command());
		MockHttpSession session = signIn();

		// Both flags posted as false. The handler's signature does not bind them, so they are not
		// merely ignored by validation — there is no parameter for them to bind to.
		this.mvc.perform(post("/customers/me/profile").session(session)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("preferredLanguage", "ja_JP")
			.param("favoriteCategory", "FISH")
			.param("myListPreference", "false")
			.param("bannerPreference", "false")
			.with(csrf())).andExpect(status().is3xxRedirection());

		Profile saved = this.registration.byUserId(USER_ID).orElseThrow().profile();
		assertThat(saved.preferredLanguage()).isEqualTo("ja_JP");
		assertThat(saved.favoriteCategory()).isEqualTo("FISH");
		// ProfileLocalHome.java:46-47 — both default true, and nothing in the slice may change them
		// while MyList and the advice banner are unbuilt.
		assertThat(saved.myListPreference()).isTrue();
		assertThat(saved.bannerPreference()).isTrue();
	}

	@Test
	@DisplayName("signon-config.xml:61 — the profile cannot be saved by an anonymous request")
	void the_profile_save_is_behind_the_security_chain() throws Exception {
		this.registration.register(command());

		this.mvc.perform(post("/customers/me/profile").contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("preferredLanguage", "ja_JP")
			.with(csrf())).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));

		assertThat(this.registration.byUserId(USER_ID).orElseThrow().profile().preferredLanguage())
			.isEqualTo("en_US");
	}

	/** Signs in for real and hands back the session the chain authenticated, cookies and all. */
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
