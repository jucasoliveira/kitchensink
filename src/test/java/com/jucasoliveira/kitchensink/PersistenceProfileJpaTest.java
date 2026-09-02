package com.jucasoliveira.kitchensink;

import java.sql.Connection;

import javax.sql.DataSource;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 1.5 — the acceptance criterion, literally: "{@code --spring.profiles.active=jpa} switches
 * the store with no code change".
 *
 * <p>Note what this class does <em>not</em> import: {@link TestcontainersConfiguration}. Under
 * {@code jpa} there is no MongoDB, no Docker daemon and no container to wait for. If that import
 * ever becomes necessary to keep this green, the switch has stopped being a switch.
 *
 * <p>There is no JPA adapter yet — 3.3 and 4.6 write those — so what is proven here is the
 * foundation they will land on: an H2 datasource, a Hibernate {@code EntityManagerFactory} with
 * an empty persistence unit, and a health endpoint that reports {@code db} rather than
 * {@code mongo}. The mirror image is {@link PersistenceProfileMongoTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa")
class PersistenceProfileJpaTest {

	@Autowired
	ApplicationContext context;

	@Autowired
	Environment environment;

	@Autowired
	DataSource dataSource;

	@Autowired
	MockMvc mvc;

	@Test
	@DisplayName("jpa is the active profile, and mongo is not accepted alongside it")
	void jpa_is_active() {
		assertThat(this.environment.getActiveProfiles()).containsExactly("jpa");
		assertThat(this.environment.matchesProfiles("mongo")).isFalse();
	}

	@Test
	@DisplayName("the document store is not in the context at all")
	void the_mongo_stack_is_absent() {
		// A MongoClient is lazy and would not fail here; its health contributor would, with a
		// 503 from /actuator/health after a 30-second server-selection timeout. So the test is
		// on the beans, not on whether the context happened to start.
		assertThat(this.context.getBeanNamesForType(MongoClient.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(MongoTemplate.class)).isEmpty();
		// The 1.1 spike repository extends MongoRepository, so it must go too — and the
		// controller that injects it is @Profile("mongo") for exactly that reason.
		assertThat(this.context.getBeanNamesForType(MemberRepository.class)).isEmpty();
	}

	@Test
	@DisplayName("the relational stack is wired: H2 in memory, named kitchensink, Hibernate on top")
	void the_relational_stack_is_present() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:kitchensink");
		}
		// By name, not by type: jakarta.persistence is a JPA-adapter concern (LayeringRulesTest)
		// and this test should not be the first thing outside that adapter to import it.
		assertThat(this.context.containsBean("entityManagerFactory")).isTrue();
	}

	@Test
	@DisplayName("/actuator/health says db, and says nothing about mongo")
	void health_reports_the_store_that_is_wired() throws Exception {
		this.mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components.db.status").value("UP"))
			.andExpect(jsonPath("$.components.mongo").doesNotExist());
	}

}
