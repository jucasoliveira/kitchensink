package com.jucasoliveira.kitchensink.catalog.adapter.web;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code banner.jsp:63-68} — the chrome's sign-on links.
 *
 * <p>The legacy banner rendered {@code Account | Cart | Sign in} and swapped the last for
 * {@code Sign out} on the {@code ${j_signon}} session flag that {@code SignOnFilter} maintained by
 * hand. The migrated banner had the logo, an "Account" link and the locale flags — and <em>no way
 * to reach a sign-on page at all</em>. {@code /login} existed and was reachable only by typing the
 * URL or by bouncing off a protected page, which is how it went unnoticed: every automated test
 * that needed a session used {@code formLogin()} directly and never looked for the link.
 *
 * <p>Three deviations from the legacy banner, all deliberate:
 * <ul>
 * <li><b>No Cart.</b> T3, deferred under ADR-0006, and a dead link would claim otherwise.</li>
 * <li><b>Sign out is a POST</b>, where {@code signoff.do} was a GET link. Spring Security's default
 * logout is CSRF-protected, and a GET that changes state is one legacy habit worth dropping.</li>
 * <li><b>A separate Register link.</b> The legacy reached {@code create_customer.screen} from the
 * sign-on page; Spring Security's generated {@code /login} has nowhere to put that, so the banner
 * carries it while anonymous.</li>
 * </ul>
 *
 * <p>Tagged {@code parity} on the two that restate a legacy rule: which link the banner shows is a
 * behaviour the 2003 app had and this one had lost.
 */
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BannerTest {

	@Autowired
	MockMvc mvc;

	@Test
	@Tag("parity")
	@DisplayName("banner.jsp:67 — an anonymous visitor is offered Sign in, and a way to register")
	void the_banner_offers_sign_in_when_anonymous() throws Exception {
		this.mvc.perform(get("/catalog"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("href=\"/login\"")))
			.andExpect(content().string(containsString("Sign in")))
			.andExpect(content().string(containsString("href=\"/customers\"")))
			.andExpect(content().string(containsString("Register")))
			// Not the signed-on half, and not a logout form a visitor cannot use.
			.andExpect(content().string(not(containsString("Sign out"))))
			.andExpect(content().string(not(containsString("/customers/me"))));
	}

	@Test
	@Tag("parity")
	@DisplayName("banner.jsp:66 — a signed-on shopper is offered Sign out, and Account reaches customer.do's screen")
	void the_banner_offers_sign_out_when_signed_on() throws Exception {
		this.mvc.perform(get("/catalog").with(user("shopper")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Sign out")))
			// customer.do (signon-config.xml:61) was the signed-on account screen, which is
			// /customers/me here — not /customers, the registration form it used to point at.
			.andExpect(content().string(containsString("href=\"/customers/me\"")))
			.andExpect(content().string(not(containsString("Sign in"))));
	}

	@Test
	@DisplayName("sign-out is a POST with a CSRF token, not signoff.do's GET link")
	void signing_out_is_a_post() throws Exception {
		// The deviation stated as an assertion. th:action is what makes Thymeleaf emit the hidden
		// token; without it every sign-out would be a 403, which is exactly the kind of thing that
		// is invisible until someone clicks it in a demo.
		this.mvc.perform(get("/catalog").with(user("shopper")))
			.andExpect(content().string(containsString("action=\"/logout\"")))
			.andExpect(content().string(containsString("method=\"post\"")))
			.andExpect(content().string(containsString("name=\"_csrf\"")));
	}

	@Test
	@DisplayName("ja/banner.jsp — the Japanese banner uses the legacy's own labels")
	void the_banner_is_localised() throws Exception {
		// ログイン and 顧客情報 are decoded from ja/banner.jsp, not translated here.
		this.mvc.perform(get("/catalog").param("locale", "ja_JP"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("ログイン")))
			.andExpect(content().string(not(containsString("Sign in"))));
	}

	@Test
	@DisplayName("ADR-0006 — there is no Cart link, because there is no cart")
	void the_deferred_cart_has_no_link() throws Exception {
		// banner.jsp:64 had "| <a href=\"cart.do\">Cart</a> |". Asserting the absence is how the
		// slice keeps its edges visible: a link to an unbuilt feature is worse than no link.
		this.mvc.perform(get("/catalog"))
			.andExpect(content().string(not(containsString("Cart"))))
			.andExpect(content().string(not(containsString("/cart"))));
	}

}
