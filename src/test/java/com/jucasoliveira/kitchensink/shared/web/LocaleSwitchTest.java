package com.jucasoliveira.kitchensink.shared.web;

import java.util.Locale;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 4.5 — {@code changelocale.do}, as a {@code LocaleChangeInterceptor} over a
 * {@code SessionLocaleResolver}.
 *
 * <h2>The legacy mechanism</h2>
 *
 * <p>{@code banner.jsp:76-105} was three {@code <waf:client_cache_link>} tags — a US, a Japanese and
 * a Chinese flag — each posting one parameter, {@code <waf:param name="locale" value="ja_JP"/>}, at
 * {@code changelocale.do}. {@code ChangeLocaleHTMLAction.java:69} put the parsed {@code Locale} on
 * the session under {@code com.sun.j2ee.blueprints.waf.LOCALE} and raised a {@code ChangeLocaleEvent};
 * every screen thereafter read the session. {@code WebI18nConfig} is those two lines: the interceptor
 * is the action, the resolver is the session attribute, and {@code locale} is still the parameter name.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>Before this issue there was no {@code LocaleResolver} bean, so Boot installed its default
 * {@code AcceptHeaderLocaleResolver} and the catalogue was chosen by the shopper's browser. The
 * legacy never read {@code Accept-Language} — a Japanese browser got the English store until the
 * flag was clicked, and the flag's effect then outlived the request.
 * {@link #the_browser_header_is_not_the_source_of_truth()} and {@link #a_switch_outlives_the_request()}
 * are the two halves of that, and they are the reason
 * {@code CatalogScreenTest.a_supported_locale_renders_that_locale_s_details} had to change shape.
 *
 * <h2>Two locale lookups, one Locale</h2>
 *
 * <p>The catalogue rows are keyed {@code ja_JP} exactly ({@code Populate-UTF8.xml}, and
 * {@code CatalogService} filters on {@code details().containsKey(locale)}); the message catalogue is
 * {@code messages_ja.properties}, keyed on the language alone. One {@code Locale("ja","JP")} feeds
 * both, by two different lookup rules — {@code Locale::toString} for the data,
 * {@code MessageSource}'s bundle walk for the strings.
 * {@link #a_switch_changes_both_the_catalogue_and_the_chrome()} asserts they move together, because
 * a page half in each language is the failure this arrangement invites.
 */
@Tag("parity")
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LocaleSwitchTest {

	/** {@code Populate-UTF8.xml} — the FISH category's product name in each catalogue. */
	static final String ANGELFISH_EN = "Angelfish";

	static final String ANGELFISH_JA = "エンゼルフィッシュ";

	/** {@code sidebar.jsp:65} against {@code ja/sidebar.jsp:65} — the chrome, not the data. */
	static final String PETS_EN = "Pets";

	static final String PETS_JA = "ペット";

	static final String FISH = "/catalog/categories/FISH";

	@Autowired
	MockMvc mvc;

	@Test
	@DisplayName("banner.jsp:87-94 — ?locale=ja_JP is the flag click, and it renders the Japanese catalogue")
	void the_locale_parameter_switches_the_catalogue() throws Exception {
		this.mvc.perform(get(FISH).param("locale", "ja_JP"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_JA)))
			.andExpect(content().string(not(containsString(ANGELFISH_EN))));
	}

	@Test
	@DisplayName("ChangeLocaleHTMLAction.java:69 — the choice is stored on the session, so it outlives the request")
	void a_switch_outlives_the_request() throws Exception {
		// The whole point of the session attribute. One flag click, then ordinary navigation with no
		// parameter on it — the legacy's client_cache_link re-encoded the request's own parameters
		// (banner.jsp:79-80) precisely so the shopper did not have to carry the locale by hand.
		MockHttpSession session = new MockHttpSession();

		this.mvc.perform(get("/catalog").param("locale", "ja_JP").session(session)).andExpect(status().isOk());

		this.mvc.perform(get(FISH).session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_JA)))
			.andExpect(content().string(not(containsString(ANGELFISH_EN))));
	}

	@Test
	@DisplayName("the choice is one shopper's, not the server's: a second session is unaffected")
	void a_switch_does_not_leak_between_sessions() throws Exception {
		// SessionLocaleResolver, not FixedLocaleResolver. Worth pinning: the failure would be
		// invisible in a single-user test and catastrophic in front of an audience, where one
		// person switching to Japanese changes everyone's screen.
		MockHttpSession japanese = new MockHttpSession();
		this.mvc.perform(get(FISH).param("locale", "ja_JP").session(japanese)).andExpect(status().isOk());

		this.mvc.perform(get(FISH).session(new MockHttpSession()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_EN)))
			.andExpect(content().string(not(containsString(ANGELFISH_JA))));
	}

	@Test
	@DisplayName("the switch is reversible — banner.jsp:76-83, the US flag")
	void a_switch_back_to_english_works() throws Exception {
		MockHttpSession session = new MockHttpSession();

		this.mvc.perform(get(FISH).param("locale", "ja_JP").session(session))
			.andExpect(content().string(containsString(ANGELFISH_JA)));

		this.mvc.perform(get(FISH).param("locale", "en_US").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_EN)))
			.andExpect(content().string(not(containsString(ANGELFISH_JA))));
	}

	@Test
	@DisplayName("SignOnNotifier.java:140 — Accept-Language is not the source of truth; the session is")
	void the_browser_header_is_not_the_source_of_truth() throws Exception {
		// SessionLocaleResolver.setDefaultLocale(Locale.US) is what makes the header irrelevant:
		// with no default set it would fall through to the request's locale and this would be
		// Japanese, which is exactly the pre-4.5 behaviour. The default stands in for
		// ProfileLocalHome.java:44 DefaultPreferredLanguage = "en_US".
		this.mvc.perform(get(FISH).locale(Locale.JAPAN))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_EN)))
			.andExpect(content().string(not(containsString(ANGELFISH_JA))));
	}

	@Test
	@DisplayName("a supported switch moves the catalogue rows and the message catalogue together")
	void a_switch_changes_both_the_catalogue_and_the_chrome() throws Exception {
		// Two lookups off one Locale — see the class comment. If messages_ja.properties were named
		// messages_ja_JP.properties this still passes; if it were named for a language the request
		// does not carry, the data would be Japanese and the sidebar English.
		this.mvc.perform(get(FISH).param("locale", "ja_JP"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_JA)))
			.andExpect(content().string(containsString(PETS_JA)))
			.andExpect(content().string(not(containsString(PETS_EN))));
	}

	@Test
	@DisplayName("an unsupported but well-formed locale falls back to en_US rather than emptying the store")
	void an_unsupported_locale_falls_back_to_english() throws Exception {
		// fr_FR parses, so the resolver accepts it and the session now holds a locale no catalogue
		// row is keyed by. CatalogController.localeKey is the guard: SUPPORTED is banner.jsp's three
		// flags, and anything else is en_US. Without it every category filter would match nothing
		// and the shopper would get an empty store rather than an English one.
		this.mvc.perform(get(FISH).param("locale", "fr_FR"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_EN)));
	}

	@Test
	@DisplayName("departure — an unparseable locale is ignored, where ChangeLocaleHTMLAction.java:73 threw")
	void a_malformed_locale_is_ignored_rather_than_fatal() throws Exception {
		// The legacy raised HTMLActionException, which the WAF rendered as general_error.screen.
		// setIgnoreInvalidLocale(true) makes a mistyped query parameter a no-op instead: the page
		// renders in whatever locale the session already held. A 500 on a hand-edited URL is not
		// worth parity, and it is the one place this issue is deliberately laxer than 2003.
		this.mvc.perform(get(FISH).param("locale", "!!!"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(ANGELFISH_EN)));
	}

	@Test
	@DisplayName("banner.jsp:76-105 — the three flags are on the page, each linking back to the screen you are on")
	void the_flag_links_carry_the_locale_back_to_the_current_screen() throws Exception {
		// ChromeAdvice.currentPath is the stand-in for client_cache_link's encodeRequestParameters
		// (banner.jsp:79-80): the flags returned you to the screen you were reading, not to the
		// front page. Asserting the rendered href is the only way to catch a link expression that
		// resolves to the wrong base and silently sends every flag click to "/".
		this.mvc.perform(get(FISH))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(FISH + "?locale=en_US")))
			.andExpect(content().string(containsString(FISH + "?locale=ja_JP")))
			.andExpect(content().string(containsString(FISH + "?locale=zh_CN")))
			.andExpect(content().string(containsString("us_flag.gif")))
			.andExpect(content().string(containsString("ja_flag.gif")))
			.andExpect(content().string(containsString("zh_flag.gif")));
	}

}
