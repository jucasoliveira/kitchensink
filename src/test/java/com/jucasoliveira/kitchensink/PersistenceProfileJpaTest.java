package com.jucasoliveira.kitchensink;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Objects;

import javax.sql.DataSource;

import com.jucasoliveira.kitchensink.customer.application.CustomerRegistration;
import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
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
	@DisplayName("the customer port has no adapter under jpa yet — the 4.6 gap, written down")
	void the_customer_port_has_no_adapter_yet() {
		// Issue 1.7 wrote the Mongo adapter only; 4.6 writes the JPA one. Until then the customer
		// port has no implementation under jpa, and nothing in src/main may inject it without a
		// @Profile("mongo") guard or this context stops starting. AGENTS.md §5: "both persistence
		// profiles stay green, or the gap is written down" — this is where it is written. When
		// 4.6 lands, this flips to hasSize(1) and moves next to its mongo twin.
		assertThat(this.context.getBeanNamesForType(CustomerRepository.class)).isEmpty();
	}

	@Test
	@DisplayName("sign-on has no customer behind it under jpa yet — the 1.8 corollary of the 4.6 gap")
	void sign_on_is_not_backed_by_the_customer_aggregate_yet() {
		// Everything in the customer context that needs the port — the registration service and
		// the UserDetailsService that SignOnTest proves under mongo — is guarded the same way the
		// port's adapter is, so this context still starts. Boot's generated in-memory user fills
		// the UserDetailsService slot meanwhile. When 4.6 lands, both guards go and this flips.
		assertThat(this.context.getBeanNamesForType(CustomerRegistration.class)).isEmpty();
		assertThat(this.context.getBeansOfType(UserDetailsService.class).values())
			.noneMatch(service -> service.getClass().getPackageName().startsWith("com.jucasoliveira.kitchensink.customer"));
	}

	@Test
	@DisplayName("the 1.9 screens and resource are absent under jpa too — the 4.6 gap, one layer up")
	void the_customer_web_adapter_is_absent_too() {
		// The controller and the REST resource inject CustomerRegistration, which is not here
		// (previous test), so they carry the same @Profile("mongo") guard or this context fails
		// to start. Same lifetime as the guard on the service: 4.6 removes all of them together.
		assertThat(Arrays.stream(this.context.getBeanDefinitionNames())
			.map(this.context::getType)
			.filter(Objects::nonNull)
			.map(Class::getPackageName))
			.noneMatch(pkg -> pkg.startsWith("com.jucasoliveira.kitchensink.customer.adapter.web"));
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
