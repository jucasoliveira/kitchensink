package com.jucasoliveira.kitchensink;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	/**
	 * The one place the MongoDB image is named. {@code compose.yaml} must agree with it, and
	 * {@link ComposeConsistencyTest} fails the build when the two drift apart — a local runtime on
	 * a different server version from the one CI tests against is a bug that only shows up in
	 * front of an audience.
	 */
	static final String MONGO_IMAGE = "mongo:7.0";

	@Bean
	@ServiceConnection
	MongoDBContainer mongoDbContainer() {
		return new MongoDBContainer(MONGO_IMAGE).withReplicaSet();
	}

}
