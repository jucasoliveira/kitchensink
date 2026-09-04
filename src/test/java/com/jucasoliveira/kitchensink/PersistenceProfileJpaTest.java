package com.jucasoliveira.kitchensink;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Objects;

import javax.sql.DataSource;

import com.jucasoliveira.kitchensink.catalog.application.CatalogRepository;
import com.jucasoliveira.kitchensink.catalog.application.CatalogService;
import com.jucasoliveira.kitchensink.customer.application.CustomerRegistration;
import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
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
 * <p>Issues 3.3 and 4.6 have since landed the catalog's and the customer's JPA adapters, so both
 * ports resolve under both profiles and the customer slice is no longer absent here. What this
 * class proves is the switch itself: an H2 datasource, a
 * Hibernate {@code EntityManagerFactory}, no Mongo stack at all, and a health endpoint that
 * reports {@code db} rather than {@code mongo}. The mirror image is
 * {@link PersistenceProfileMongoTest}; that the two stores actually answer the same questions is
 * {@code CatalogRepositoryContract}'s job, not this one's.
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
	@DisplayName("the customer port has exactly one adapter, and under jpa it is the JPA one — issue 4.6")
	void the_customer_port_is_bound_to_the_jpa_adapter() {
		// This test used to assert isEmpty(): issue 1.7 wrote the Mongo adapter only, so the port
		// had no implementation under jpa and every collaborator carried a @Profile("mongo") guard
		// to keep this context starting. AGENTS.md §5 asks for "both profiles green, or the gap is
		// written down", and this is where the gap was written down. 4.6 closed it, so the
		// assertion inverts rather than the comment being deleted — the gap is part of the record.
		//
		// That the two adapters answer the SAME questions is CustomerRepositoryContract's job,
		// run once per profile; this only pins the wiring.
		assertThat(this.context.getBeanNamesForType(CustomerRepository.class)).hasSize(1);
		Class<?> adapter = AopProxyUtils.ultimateTargetClass(this.context.getBean(CustomerRepository.class));
		assertThat(adapter.getPackageName())
			.isEqualTo("com.jucasoliveira.kitchensink.customer.adapter.persistence.jpa");
	}

	@Test
	@DisplayName("sign-on is backed by the customer aggregate under jpa too — the 1.8 corollary of 4.6")
	void sign_on_is_backed_by_the_customer_aggregate() {
		// The inverse of what this asserted before 4.6. The registration service and the
		// UserDetailsService were both @Profile("mongo") because the port had no jpa adapter, and
		// Boot's generated in-memory user filled the UserDetailsService slot in their absence.
		// With the adapter in place both guards came off, so a shopper registered under jpa can
		// sign on with the password they registered — and Boot's generated user is gone, which is
		// the half worth asserting: a UserDetailsService that silently stayed the in-memory one
		// would let SignOnTest's mongo-side proof stand in for a jpa side that never worked.
		assertThat(this.context.getBeanNamesForType(CustomerRegistration.class)).hasSize(1);
		assertThat(this.context.getBeansOfType(UserDetailsService.class).values())
			.anyMatch(service -> service.getClass().getPackageName().startsWith("com.jucasoliveira.kitchensink.customer"));
	}

	@Test
	@DisplayName("the 1.9 screens and the 4.7 resource are present under jpa too — 4.6, one layer up")
	void the_customer_web_adapter_is_present_too() {
		// Also inverted by 4.6. The controller, the REST resource and the sign-on success handler
		// all inject CustomerRegistration, so before the jpa adapter existed they carried the same
		// @Profile("mongo") guard or this context failed to start. Five guards came off together —
		// the service, the UserDetailsService, and these three — because leaving any one of them
		// behind would mean the switch was still half-done.
		assertThat(Arrays.stream(this.context.getBeanDefinitionNames())
			.map(this.context::getType)
			.filter(Objects::nonNull)
			.map(Class::getPackageName))
			.anyMatch(pkg -> pkg.startsWith("com.jucasoliveira.kitchensink.customer.adapter.web"));
	}

	@Test
	@DisplayName("the catalog port has exactly one adapter, and under jpa it is the JPA one — issue 3.3")
	void the_catalog_port_is_bound_to_the_jpa_adapter() {
		// The same shape as the customer port above, and the one that got there first. One port,
		// two adapters, and the only thing deciding which store answers is --spring.profiles.active.
		// That the two adapters answer the *same* questions is proven by CatalogRepositoryContract,
		// which runs once per profile; this only pins the wiring.
		// Unwrapped in case @Transactional's proxy sits in front of the bean.
		assertThat(this.context.getBeanNamesForType(CatalogRepository.class)).hasSize(1);
		Class<?> adapter = AopProxyUtils.ultimateTargetClass(this.context.getBean(CatalogRepository.class));
		assertThat(adapter.getPackageName())
			.isEqualTo("com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa");
		// 3.3 also took @Profile("mongo") off CatalogService: with an adapter on both sides of the
		// port, the guard had nothing left to guard, and the service is store-agnostic by
		// construction (LayeringRulesTest.the_application_layer_does_not_know_its_adapters).
		assertThat(this.context.getBeanNamesForType(CatalogService.class)).hasSize(1);
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
