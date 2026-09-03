package com.jucasoliveira.kitchensink;

import javax.sql.DataSource;

import com.jucasoliveira.kitchensink.catalog.application.CatalogRepository;
import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 1.5 — the {@code mongo} side of the switch, in a running context.
 *
 * <p>Deliberately activates <em>no</em> profile: ADR-0005 §4 says "{@code mongo} (default)", and
 * the way to test a default is to not name it. Everything asserted here is what a developer gets
 * from {@code scripts/dev-up.sh && scripts/run.sh} with no further arguments.
 *
 * <p>The mirror image is {@link PersistenceProfileJpaTest}; the file that makes both true is
 * pinned by {@link ProfileConfigurationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PersistenceProfileMongoTest {

	@Autowired
	ApplicationContext context;

	@Autowired
	Environment environment;

	@Autowired
	MockMvc mvc;

	@Test
	@DisplayName("with nothing activated, the context runs as mongo")
	void mongo_is_the_default_profile() {
		assertThat(this.environment.getActiveProfiles()).isEmpty();
		assertThat(this.environment.getDefaultProfiles()).containsExactly("mongo");
		assertThat(this.environment.acceptsProfiles(Profiles.of("mongo"))).isTrue();
	}

	@Test
	@DisplayName("the document store is wired: client, template, repositories")
	void the_mongo_stack_is_present() {
		assertThat(this.context.getBeanNamesForType(MongoClient.class)).hasSize(1);
		assertThat(this.context.getBeanNamesForType(MongoTemplate.class)).hasSize(1);
		// The 1.1 spike repository is the only MongoRepository so far; E4 replaces it with the
		// customer adapter, at which point this line changes with it.
		assertThat(this.context.getBeanNamesForType(MemberRepository.class)).hasSize(1);
	}

	@Test
	@DisplayName("the customer port has exactly one adapter, and under mongo it is the Mongo one")
	void the_customer_port_is_bound_to_the_mongo_adapter() {
		// Issue 1.7. The port is an interface in the application layer (LayeringRulesTest); which
		// implementation it resolves to is a profile decision, and this is the mongo half of it.
		// Unwrapped in case @Repository's exception-translation proxy sits in front of the bean.
		assertThat(this.context.getBeanNamesForType(CustomerRepository.class)).hasSize(1);
		Class<?> adapter = AopProxyUtils.ultimateTargetClass(this.context.getBean(CustomerRepository.class));
		assertThat(adapter.getPackageName()).isEqualTo("com.jucasoliveira.kitchensink.customer.adapter.persistence.mongo");
	}

	@Test
	@DisplayName("the catalog port has exactly one adapter, and under mongo it is the Mongo one — issue 3.3")
	void the_catalog_port_is_bound_to_the_mongo_adapter() {
		// The other half of the switch, stated where its jpa twin is stated. Both halves are
		// wiring assertions; CatalogRepositoryContract is what proves the two adapters agree.
		assertThat(this.context.getBeanNamesForType(CatalogRepository.class)).hasSize(1);
		Class<?> adapter = AopProxyUtils.ultimateTargetClass(this.context.getBean(CatalogRepository.class));
		assertThat(adapter.getPackageName())
			.isEqualTo("com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo");
	}

	@Test
	@DisplayName("the relational stack is not in the context at all")
	void the_relational_stack_is_absent() {
		// Not "present but idle": H2 is on the classpath under both profiles, and left to itself
		// Boot would pool an embedded database and boot Hibernate against it on every start.
		assertThat(this.context.getBeanNamesForType(DataSource.class)).isEmpty();
		assertThat(this.context.containsBean("entityManagerFactory")).isFalse();
	}

	@Test
	@DisplayName("/actuator/health says mongo, and says nothing about a db")
	void health_reports_the_store_that_is_wired() throws Exception {
		// Permitted to anonymous callers by SecurityConfig, so no authentication here — which is
		// also why ProfileConfigurationTest insists on show-components rather than show-details.
		this.mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components.mongo.status").value("UP"))
			.andExpect(jsonPath("$.components.db").doesNotExist());
	}

}
