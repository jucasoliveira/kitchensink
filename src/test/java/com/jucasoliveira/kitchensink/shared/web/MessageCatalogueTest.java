package com.jucasoliveira.kitchensink.shared.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Issue 4.5 — the two message catalogues, as documents.
 *
 * <h2>What replaced what</h2>
 *
 * <p>The legacy had no message catalogue. Localisation was structural: {@code screendefinitions_en_US.xml}
 * and {@code screendefinitions_ja_JP.xml} are the same nineteen {@code <screen>} elements twice, each
 * naming a different set of JSPs — {@code /customer.jsp} against {@code /ja/customer.jsp} — plus a
 * {@code <parameter key="title">} carrying the one string the template rendered directly. Translating
 * a page meant forking the page. Here there is one template per screen and two properties files, so
 * this test stands where a diff of the two screendefinition files used to be the only check available.
 *
 * <h2>The absent keys are the interesting assertion</h2>
 *
 * <p>{@code screendefinitions_ja_JP.xml} swapped every {@code body} to a {@code /ja/} JSP — including
 * {@code create_customer} (:120) and {@code duplicate_account} (:171) — but left those two screens'
 * {@code title} in English. So the Japanese page had a Japanese body under an English title. That is
 * not a bug we inherited by accident: it is what shipped, and
 * {@link #the_screens_the_legacy_left_untranslated_are_still_untranslated()} pins it. The mechanism
 * is the absence of a key, which {@code MessageSource} resolves by falling back to the base bundle —
 * so "do not add these two for completeness" is enforced rather than commented.
 *
 * <p>Reading the properties files directly rather than through a {@code MessageSource} is deliberate:
 * the risk being covered here is content (a missing key, mojibake, a title that drifted from the
 * legacy's), and content is cheaper to assert on the file. That the wiring resolves them at all is
 * {@link LocaleSwitchTest}'s job, against a rendered page.
 */
class MessageCatalogueTest {

	static final String BASE = "messages.properties";

	static final String JAPANESE = "messages_ja.properties";

	/**
	 * {@code screendefinitions_ja_JP.xml:120,171} — body swapped to {@code /ja/}, title left in
	 * English. See the class comment.
	 */
	static final Set<String> DELIBERATELY_UNTRANSLATED = Set.of("screen.create_customer.title",
			"screen.duplicate_account.title");

	/** The reference tree is git-ignored and absent in CI — see {@code LegacySeedCopyIsVerbatimTest}. */
	static final Path SCREENDEFINITIONS_EN_US = Path.of("petstore1.3.1_02", "src", "apps", "petstore", "src",
			"docroot", "WEB-INF", "screendefinitions_en_US.xml");

	@Test
	@DisplayName("every Japanese key names a key the base catalogue defines")
	void the_japanese_catalogue_has_no_orphan_keys() throws IOException {
		// An orphan is a translation of a key nothing looks up any more — usually the residue of a
		// renamed template. It is silent in both languages: the English page keeps working and the
		// Japanese one falls back, so nothing renders wrong and the file quietly rots.
		Properties base = load(BASE);
		Properties japanese = load(JAPANESE);

		assertThat(japanese.stringPropertyNames()).isNotEmpty().allSatisfy(key -> assertThat(base)
			.as("messages_ja.properties defines '%s', which messages.properties does not", key)
			.containsKey(key));
	}

	@Test
	@Tag("parity")
	@DisplayName("screendefinitions_ja_JP.xml:120,171 — create_customer and duplicate_account keep their English titles")
	void the_screens_the_legacy_left_untranslated_are_still_untranslated() throws IOException {
		Properties base = load(BASE);
		Properties japanese = load(JAPANESE);

		// Present in the base catalogue: these screens exist and are titled.
		assertThat(base.stringPropertyNames()).containsAll(DELIBERATELY_UNTRANSLATED);

		// Absent from the Japanese one, which is how the fallback to English happens. Adding either
		// key would be a nicer product and a worse migration: the deliverable is what Pet Store did.
		assertThat(japanese.stringPropertyNames()).doesNotContainAnyElementsOf(DELIBERATELY_UNTRANSLATED);
	}

	@Test
	@DisplayName("the Japanese catalogue decodes as UTF-8, not as the platform default")
	void the_japanese_catalogue_is_utf_8() throws IOException {
		// Boot decodes messages with spring.messages.encoding, which defaults to UTF-8. A file
		// written as Shift-JIS or MacRoman loads without error and renders as mojibake, so the
		// failure mode is a page that looks broken and a build that is green. One known value
		// read back through the same decoder Boot uses is enough to catch it.
		Properties japanese = load(JAPANESE);

		assertThat(japanese.getProperty("sidebar.pets")).isEqualTo("ペット");
		assertThat(japanese.getProperty("screen.customer.title")).isEqualTo("顧客情報");
		// U+FFFD is what a bad decode leaves behind; it must appear nowhere in the file.
		assertThat(japanese.stringPropertyNames())
			.allSatisfy(key -> assertThat(japanese.getProperty(key)).doesNotContain("�"));
	}

	@Test
	@DisplayName("no key in either catalogue resolves to an empty string")
	void no_key_is_blank() {
		// A blank value renders as an empty element rather than falling back, so it is worse than
		// an absent key: the screen loses its label and the MessageSource never gets a chance.
		for (String file : new String[] { BASE, JAPANESE }) {
			Properties properties = loadUnchecked(file);
			assertThat(properties.stringPropertyNames())
				.allSatisfy(key -> assertThat(properties.getProperty(key)).as("%s: %s", file, key).isNotBlank());
		}
	}

	@Test
	@Tag("parity")
	@DisplayName("the screen titles are screendefinitions_en_US.xml's, word for word")
	void the_english_titles_match_the_legacy_screendefinitions() throws IOException {
		// The one place the catalogue can silently diverge from the evidence. CatalogScreenTest
		// asserts these strings reach the <title> element, but it asserts them against a copy of
		// the literal, so both could drift together. This compares against the original file.
		assumeTrue(Files.isRegularFile(SCREENDEFINITIONS_EN_US),
				"reference tree not present (git-ignored, developer machines only) — nothing to compare against");

		String xml = Files.readString(SCREENDEFINITIONS_EN_US, StandardCharsets.UTF_8);
		Properties base = load(BASE);

		assertThat(base.stringPropertyNames()).filteredOn(key -> key.startsWith("screen."))
			.isNotEmpty()
			.allSatisfy(key -> {
				String screen = key.substring("screen.".length(), key.length() - ".title".length());
				assertThat(titleOf(xml, screen)).as("<screen name=\"%s\"> title", screen)
					.isEqualTo(base.getProperty(key));
			});
	}

	/** The {@code <parameter key="title" value="..."/>} of one {@code <screen name="...">}. */
	private static String titleOf(String xml, String screen) {
		Matcher matcher = Pattern
			.compile("<screen name=\"" + Pattern.quote(screen) + "\">\\s*<parameter key=\"title\" value=\"([^\"]*)\"")
			.matcher(xml);
		assertThat(matcher.find()).as("screendefinitions_en_US.xml has no <screen name=\"%s\">", screen).isTrue();
		return matcher.group(1);
	}

	private static Properties load(String name) throws IOException {
		Properties properties = new Properties();
		try (InputStream in = new ClassPathResource(name).getInputStream()) {
			// The same decoder Boot uses, so a mis-encoded file fails here the way it would render.
			properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		return properties;
	}

	private static Properties loadUnchecked(String name) {
		try {
			return load(name);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not read " + name, ex);
		}
	}

}
