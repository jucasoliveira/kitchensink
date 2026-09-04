package com.jucasoliveira.kitchensink;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue 1.1 — the skeleton spike. Proves Boot 4.1.1 on Java 21 carries the whole target stack:
 * web, security, Thymeleaf, Spring Data MongoDB and events. It is a stack probe, not a slice:
 * everything it touches is throwaway and gets replaced by E4.
 *
 * <p>The security filter chain is left <em>on</em> deliberately — a spike that disables the thing
 * it claims to have proven has proven nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, SkeletonSpikeTest.SpikeBeans.class })
class SkeletonSpikeTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberRepository members;

	@Autowired
	MongoTemplate template;

	@Autowired
	Validator validator;

	@Autowired
	Registrar registrar;

	@Autowired
	Recorder recorder;

	@BeforeEach
	void reset() {
		this.members.deleteAll();
		this.recorder.seen.clear();
	}

	@Test
	@DisplayName("a page renders through a live security filter chain, and \"/\" is the store front")
	void one_page_renders() throws Exception {
		// 1.1 pointed this at "/" and a Members table of its own. E4 replaced both halves, as the
		// class javadoc says it would: "/" is now the store front — legacy index.jsp was demo
		// harness ("enter the store" after populating.jsp, index.jsp:77-85) and the application
		// began at main.screen (screendefinitions_en_US.xml:45) — and the skeleton's own Thymeleaf
		// page is the 1.9 registration screen, which is still read out of Mongo through
		// CustomerRegistration.registered() and still sits behind the chain.
		this.mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/catalog"));

		this.mvc.perform(get("/customers"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("name=\"userId\"")));
	}

	@Test
	@DisplayName("the filter chain is genuinely in front of the dispatcher")
	void an_unpermitted_url_is_intercepted_before_it_reaches_the_dispatcher() throws Exception {
		// Not a 404: the chain rejects it before handler mapping runs. Replaces SignOnFilter,
		// which did the same job by hand — 01-legacy-architecture.md, finding #1. Issue 1.8
		// takes this further with BCrypt and a real UserDetailsService.
		this.mvc.perform(get("/no-such-page"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("the customer graph round-trips as ONE document, with the value object embedded")
	void one_document_round_trips() {
		Member saved = this.members
			.save(new Member(null, "Ada", "ada@example.com", new Member.Address("1 Main", "London")));

		Member loaded = this.members.findById(saved.id()).orElseThrow();
		assertThat(loaded.name()).isEqualTo("Ada");
		assertThat(loaded.address().city()).isEqualTo("London");

		// The claim ADR-0006 makes about the CMP graph: AddressEJB / ContactInfoEJB were declared
		// in four ejb-jar.xml files apiece purely because EJB 2.0 CMP relationships cannot cross
		// jar boundaries (finding #4). Here the address is a subdocument, not a second collection.
		Document raw = this.template.getCollection("members").find().first();
		assertThat(raw).isNotNull();
		assertThat(raw.get("address", Document.class)).containsEntry("city", "London");
		// Named one by one rather than "exactly one collection in the database". The catalog suites
		// share the Testcontainers instance and seed products and items into it, so containsExactly
		// was passing on test ordering rather than on the claim being made — issue 7.4's
		// profile-switch script found the same latent coupling in CustomerMongoRoundTripTest, and
		// adding MongoCatalogIndexTest was enough to shift the order and surface this one.
		assertThat(this.template.getDb().listCollectionNames().into(new ArrayList<>()))
			.contains("members")
			.doesNotContain("addresses", "contactinfo");
	}

	@Test
	@DisplayName("Jakarta Validation is wired and replaces the hand-rolled checks")
	void bean_validation_is_active() {
		Set<ConstraintViolation<Member>> violations = this.validator
			.validate(new Member(null, "", "not-an-email", null));

		assertThat(violations).extracting(v -> v.getPropertyPath().toString())
			.containsExactlyInAnyOrder("name", "email");
	}

	@Test
	@DisplayName("an event published in a transaction fires only after the commit")
	void transactional_event_fires_after_commit() {
		this.registrar.register(new Member(null, "Grace", "grace@example.com", new Member.Address("2 Main", "NY")));

		assertThat(this.recorder.seen).containsExactly("grace@example.com");
		assertThat(this.members.existsByEmail("grace@example.com")).isTrue();
	}

	/** Stand-in for the JMS message the legacy app would have queued here. */
	record Registered(String email) {
	}

	static class Registrar {

		private final MemberRepository repository;

		private final ApplicationEventPublisher events;

		Registrar(MemberRepository repository, ApplicationEventPublisher events) {
			this.repository = repository;
			this.events = events;
		}

		@Transactional
		void register(Member member) {
			Member saved = this.repository.save(member);
			this.events.publishEvent(new Registered(saved.email()));
		}

	}

	static class Recorder {

		final List<String> seen = new ArrayList<>();

		@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
		void on(Registered event) {
			this.seen.add(event.email());
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SpikeBeans {

		/**
		 * Boot does <em>not</em> auto-configure this, and without it {@code @Transactional} is a
		 * no-op against Mongo, which silently downgrades @TransactionalEventListener to "never
		 * fires". It also requires a replica set — see TestcontainersConfiguration. If ADR-0004
		 * is ever un-deferred, this bean moves to src/main and compose.yaml grows a replica set.
		 */
		@Bean
		MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
			return new MongoTransactionManager(factory);
		}

		@Bean
		Registrar registrar(MemberRepository repository, ApplicationEventPublisher events) {
			return new Registrar(repository, events);
		}

		@Bean
		Recorder recorder() {
			return new Recorder();
		}

	}

}
