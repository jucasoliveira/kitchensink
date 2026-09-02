package com.jucasoliveira.kitchensink;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.mongodb.ConnectionString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 1.3 — the local runtime, pinned.
 *
 * <p>The legacy equivalent of this file is {@code setup.xml:47}, whose {@code core} target
 * provisioned the world before the app could start: {@code create_jms_queues}, {@code create_users}
 * and three Cloudscape databases registered as XA datasources by hand
 * ({@code create_petstore_db} at {@code setup.xml:199}, {@code create_supplier_db} at
 * {@code :209}, {@code create_opc_db} at {@code :219} — one per EAR, because EJB 2.0 CMP could not
 * share a schema across jars). Three schema-creation targets become zero: MongoDB creates the
 * collection on first write.
 *
 * <p>What replaces them is a five-line {@code compose.yaml}, and the risk moves with it. Nothing
 * else in the build reads that file — Testcontainers starts its own container, so every test can
 * be green while {@code docker compose up && ./mvnw spring-boot:run} is broken, or while the two
 * run different server versions. These assertions are the seam: they are the only thing that ties
 * the file a developer runs to the configuration the application boots with.
 *
 * <p>No Spring context and no Docker daemon — this reads the files as text, so it costs
 * milliseconds and fails on a laptop with Docker Desktop shut down.
 */
class ComposeConsistencyTest {

	private static final Path ROOT = projectRoot();

	private static final Map<String, Object> COMPOSE = compose();

	private static final Properties APPLICATION = application();

	@Test
	@DisplayName("MongoDB is the only service: issue 1.3's 'optional Postgres' was dropped, deliberately")
	void mongodb_is_the_only_service() {
		// ADR-0005 makes the second persistence adapter JPA/H2, which is in-process and needs no
		// container, so a Postgres here would be a service nothing connects to. If a relational
		// database ever earns its place in the local runtime, that is an ADR, not a silent edit.
		assertThat(services().keySet()).containsExactly("mongo");
	}

	@Test
	@DisplayName("compose.yaml and the Testcontainers config name the same image")
	void the_local_image_matches_the_one_the_tests_run_against() {
		assertThat(mongo().get("image")).isEqualTo(TestcontainersConfiguration.MONGO_IMAGE);
	}

	@Test
	@DisplayName("the published port is the one the application dials")
	void the_published_port_matches_the_connection_string() {
		assertThat(uri().getHosts()).containsExactly("localhost:" + publishedPort());
	}

	@Test
	@DisplayName("the application targets the kitchensink database, not Boot's default")
	void the_connection_string_names_the_kitchensink_database() {
		// spring.mongodb.uri defaults to mongodb://localhost/test. Omitting the database name does
		// not fail, it just writes everything to a database called "test" — the failure mode this
		// assertion exists for, because nothing else notices.
		assertThat(uri().getDatabase()).isEqualTo("kitchensink");
	}

	@Test
	@DisplayName("the Boot 3 property spelling is not used: it is an error-level deprecation in Boot 4")
	void the_driver_properties_use_the_boot_4_namespace() {
		// spring.data.mongodb.* moved to spring.mongodb.* in Boot 4.0.0 with deprecation level
		// "error" — the binder rejects the old spelling rather than warning about it.
		assertThat(APPLICATION.stringPropertyNames()).noneMatch(name -> name.startsWith("spring.data.mongodb."))
			.contains("spring.mongodb.uri");
	}

	@Test
	@DisplayName("compose runs a replica set, because the tests do and @Transactional needs one")
	void compose_runs_a_single_node_replica_set() {
		// TestcontainersConfiguration calls withReplicaSet(). A bare mongod locally would pass
		// every test and then fail the first time anything opens a transaction — see the
		// MongoTransactionManager note in SkeletonSpikeTest.SpikeBeans.
		assertThat(command()).contains("--replSet");
		assertThat(healthcheckScript()).contains("rs.initiate");
	}

	@Test
	@DisplayName("the healthcheck reports ready only on an elected primary, which is what --wait waits for")
	void the_healthcheck_is_what_makes_dev_up_deterministic() {
		// scripts/dev-up.sh runs `docker compose up -d --wait`, so that run.sh immediately after
		// it cannot race the election. Without a healthcheck --wait degrades to "container is
		// running", which is true several seconds before Mongo will accept a write.
		assertThat(mongo()).containsKey("healthcheck");
		assertThat(healthcheckScript()).contains("isWritablePrimary");
	}

	private static ConnectionString uri() {
		String value = APPLICATION.getProperty("spring.mongodb.uri");
		assertThat(value).as("spring.mongodb.uri in src/main/resources/application.properties").isNotNull();
		return new ConnectionString(value);
	}

	private static String publishedPort() {
		List<?> ports = (List<?>) mongo().get("ports");
		assertThat(ports).as("published ports of the mongo service").hasSize(1);
		String mapping = String.valueOf(ports.get(0));
		return mapping.substring(0, mapping.indexOf(':'));
	}

	private static String command() {
		return words(mongo().get("command"));
	}

	private static String healthcheckScript() {
		Map<String, Object> healthcheck = map(mongo().get("healthcheck"));
		return words(healthcheck.get("test"));
	}

	private static Map<String, Object> mongo() {
		return map(services().get("mongo"));
	}

	private static Map<String, Object> services() {
		return map(COMPOSE.get("services"));
	}

	private static Map<String, Object> compose() {
		try (Reader reader = Files.newBufferedReader(ROOT.resolve("compose.yaml"))) {
			return map(new Yaml().load(reader));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static Properties application() {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(ROOT.resolve("src/main/resources/application.properties"))) {
			properties.load(reader);
			return properties;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Walks up from the working directory rather than trusting it. Surefire runs from the project
	 * basedir, but this project moves {@code build.directory} off the volume it lives on, and a
	 * test that reads repository files should not be the thing that quietly breaks next time
	 * something like that changes.
	 */
	private static Path projectRoot() {
		Path directory = Path.of("").toAbsolutePath();
		while (directory != null && !Files.exists(directory.resolve("compose.yaml"))) {
			directory = directory.getParent();
		}
		if (directory == null) {
			throw new IllegalStateException("no compose.yaml above " + Path.of("").toAbsolutePath());
		}
		return directory;
	}

	/** Compose accepts both list and scalar forms; flatten to one string and match on it. */
	private static String words(Object value) {
		return ((List<?>) value).stream().map(String::valueOf).collect(Collectors.joining(" "));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(Object value) {
		return (Map<String, Object>) value;
	}

}
