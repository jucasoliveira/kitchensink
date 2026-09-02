package com.jucasoliveira.kitchensink;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 1.5 — the persistence switch and the externalised settings, pinned as text.
 *
 * <p>The legacy app chose its store at deploy time, through JNDI. The catalog component's
 * {@code ejb-jar.xml:58} declares an env-entry {@code param/CatalogDAOClass};
 * {@code CatalogDAOFactory.java:58} looks it up via {@code JNDINames.CATALOG_DAO_CLASS} and
 * {@code Class.forName()}s the answer. Every connection detail was baked in the same way:
 * {@code sun-j2ee-ri.xml:361-365} carries the datasource JNDI name <em>and</em> the
 * {@code estoreuser}/{@code estore} credentials, and {@code :59} hard-codes the SQL statement file
 * as {@code http://localhost:8000/petstore/CatalogDAOSQL.xml}. Pointing the app at a different
 * database meant editing a descriptor inside the EAR and redeploying it.
 *
 * <p>The replacement is one {@code application.yaml} in three documents: defaults, {@code mongo},
 * {@code jpa}. The choice of store is a profile (ADR-0005 §4), the connection details are
 * environment variables with a localhost default, and the store that is <em>not</em> chosen is
 * switched off with {@code spring.autoconfigure.exclude} — configuration, not an {@code @Profile}
 * on code, which is what "switches the store with no code change" has to mean.
 *
 * <p>{@link PersistenceProfileMongoTest} and {@link PersistenceProfileJpaTest} prove the outcome
 * inside a running context. This class pins the shape of the file, so that a change which keeps
 * those two green by accident — a Mongo URI creeping into the defaults, say — is still noticed.
 * No Spring context, no Docker: milliseconds.
 */
class ProfileConfigurationTest {

	private static final ApplicationYaml DEFAULTS = ApplicationYaml.defaults();

	private static final ApplicationYaml MONGO = ApplicationYaml.profile("mongo");

	private static final ApplicationYaml JPA = ApplicationYaml.profile("jpa");

	private static final String DATASOURCE_AUTOCONFIGURATION = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration";

	/**
	 * The three Mongo auto-configurations that must go. {@code MongoAutoConfiguration} owns the
	 * client; {@code DataMongoAutoConfiguration} the template; and the repositories one has no
	 * {@code @ConditionalOnBean} guard at all — leave it in and it registers every
	 * {@code MongoRepository} on the classpath, which then fails to wire for want of a template.
	 * The health contributor is {@code @ConditionalOnBean} and disappears on its own.
	 */
	private static final String[] MONGO_AUTOCONFIGURATIONS = {
			"org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration",
			"org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
			"org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration" };

	@Test
	@DisplayName("no profile named means mongo: ADR-0005 §4 makes it the default")
	void mongo_is_the_default_profile() {
		// spring.profiles.default only takes effect from the profile-less document, so this is
		// also an assertion about where it lives.
		assertThat(DEFAULTS.value("spring.profiles.default")).isEqualTo("mongo");
	}

	@Test
	@DisplayName("the defaults document is store-neutral")
	void the_defaults_document_names_no_store() {
		// Anything store-specific here would apply under both profiles, and the switch would be
		// partial — which is the failure mode that is hardest to see, because both still boot.
		assertThat(DEFAULTS.keys()).noneMatch(key -> key.startsWith("spring.mongodb.")
				|| key.startsWith("spring.datasource.") || key.startsWith("spring.jpa.")
				|| key.startsWith("spring.autoconfigure."));
	}

	@Test
	@DisplayName("under mongo, the relational stack is switched off by configuration alone")
	void under_mongo_the_relational_stack_is_excluded() {
		// Everything JPA hangs off the DataSource bean: HibernateJpaConfiguration is
		// @ConditionalOnSingleCandidate(DataSource), the JPA repositories and the "db" health
		// contributor are @ConditionalOnBean(DataSource). One exclusion collapses the lot.
		assertThat(MONGO.list("spring.autoconfigure.exclude")).contains(DATASOURCE_AUTOCONFIGURATION);
		assertThat(MONGO.keys()).noneMatch(key -> key.startsWith("spring.datasource.") || key.startsWith("spring.jpa."));
	}

	@Test
	@DisplayName("under jpa, the Mongo stack is switched off by configuration alone")
	void under_jpa_the_mongo_stack_is_excluded() {
		assertThat(JPA.list("spring.autoconfigure.exclude")).contains(MONGO_AUTOCONFIGURATIONS);
		assertThat(JPA.keys()).noneMatch(key -> key.startsWith("spring.mongodb."));
	}

	@Test
	@DisplayName("connection details come from the environment, with a localhost default")
	void connection_details_are_overridable_without_a_rebuild() {
		// sun-j2ee-ri.xml:361-365 baked the datasource and its credentials into the EAR. Here
		// the URI is an environment variable, and the default is what `scripts/dev-up.sh`
		// followed by `scripts/run.sh` needs on a laptop — nothing else.
		assertThat(MONGO.raw("spring.mongodb.uri")).matches("\\$\\{MONGODB_URI:.+}");
		assertThat(JPA.raw("spring.datasource.url")).matches("\\$\\{JDBC_URL:.+}");
	}

	@Test
	@DisplayName("the jpa profile runs H2 in memory, and the database is called kitchensink")
	void the_jpa_profile_runs_h2_in_memory() {
		// In-process, so the profile-switch demo needs no second container (see
		// ComposeConsistencyTest.mongodb_is_the_only_service). Named, so the H2 console and the
		// logs say "kitchensink" rather than the random UUID Boot generates for embedded databases.
		assertThat(JPA.value("spring.datasource.url")).startsWith("jdbc:h2:mem:kitchensink");
	}

	@Test
	@DisplayName("health names the store that is wired, without leaking its details to anyone")
	void health_shows_components_but_not_details() {
		// show-components lists "mongo" or "db" — that is the visible proof of the switch, and
		// it is what the two runtime tests assert on. show-details would add server versions
		// and validation queries, and SecurityConfig permits /actuator/health to anonymous
		// callers, so that stays off for them.
		assertThat(DEFAULTS.value("management.endpoint.health.show-components")).isEqualTo("always");
		assertThat(DEFAULTS.value("management.endpoint.health.show-details")).isNotEqualTo("always");
	}

	@Test
	@DisplayName("the Boot 3 property spelling is not used: it is an error-level deprecation in Boot 4")
	void the_driver_properties_use_the_boot_4_namespace() {
		// spring.data.mongodb.* moved to spring.mongodb.* in Boot 4.0.0 with deprecation level
		// "error" — the binder rejects the old spelling rather than warning about it.
		for (ApplicationYaml document : ApplicationYaml.allDocuments()) {
			assertThat(document.keys()).as("%s", document).noneMatch(key -> key.startsWith("spring.data.mongodb."));
		}
	}

}
