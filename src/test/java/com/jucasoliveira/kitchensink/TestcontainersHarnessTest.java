package com.jucasoliveira.kitchensink;

import com.mongodb.ConnectionString;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 1.10 — the Testcontainers gate, proven rather than observed.
 *
 * <p>Every {@code @SpringBootTest} in this suite imports {@link TestcontainersConfiguration} and
 * goes green. What none of them shows is that the context is talking to the container that
 * configuration started: on a laptop with {@code docker compose up} running, a broken
 * {@code @ServiceConnection} could fall through to {@code localhost:27017} and every slice test
 * would still pass — against the developer's database, with the developer's data in it. In CI
 * the same fault is a connection refused, which is a red gate for the wrong reason.
 *
 * <p>So this class asks the running context where it is connected and what is on the other end,
 * and pins each answer to the container. It is the runtime twin of {@link ComposeConsistencyTest},
 * which pins the same facts as text. Same {@code @Import} as the round-trip test, so it shares
 * that cached context and costs no extra container.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TestcontainersHarnessTest {

	@Autowired
	MongoDBContainer container;

	@Autowired
	MongoConnectionDetails connection;

	@Autowired
	MongoTemplate template;

	@Test
	@DisplayName("the context is wired to the container this configuration started, not to compose's port")
	void the_context_is_connected_to_the_container_it_started() {
		assertThat(this.container.isRunning()).isTrue();

		// @ServiceConnection is what turns the container into a MongoConnectionDetails bean, and
		// it is the only thing that should. Host and mapped port, compared exactly: a fallback to
		// spring.mongodb.uri's localhost:27017 default is the failure this exists to catch.
		ConnectionString wired = this.connection.getConnectionString();
		ConnectionString started = new ConnectionString(this.container.getConnectionString());
		assertThat(wired.getHosts()).isEqualTo(started.getHosts());
		assertThat(wired.getHosts()).containsExactly(this.container.getHost() + ":" + this.container.getFirstMappedPort());
	}

	@Test
	@DisplayName("the server on the other end is the image TestcontainersConfiguration names")
	void the_server_is_the_named_image() {
		// ComposeConsistencyTest proves compose.yaml names the same image as the tests. This is the
		// other half: that the tests actually ran against it. "mongo:7.0" -> a 7.0.x server.
		String tag = TestcontainersConfiguration.MONGO_IMAGE.substring(TestcontainersConfiguration.MONGO_IMAGE.indexOf(':') + 1);
		Document buildInfo = this.template.getDb().runCommand(new Document("buildInfo", 1));
		assertThat(buildInfo.getString("version")).startsWith(tag + ".");
	}

	@Test
	@DisplayName("the container is a writable replica-set primary, which is what @Transactional needs")
	void the_container_runs_a_replica_set() {
		// withReplicaSet() in TestcontainersConfiguration; compose_runs_a_single_node_replica_set
		// pins the compose side. A bare mongod here would pass every test until the first
		// transaction — see the MongoTransactionManager note in SkeletonSpikeTest.
		Document hello = this.template.getDb().runCommand(new Document("hello", 1));
		assertThat(hello.getString("setName")).isNotBlank();
		assertThat(hello.getBoolean("isWritablePrimary")).isTrue();
	}

}
