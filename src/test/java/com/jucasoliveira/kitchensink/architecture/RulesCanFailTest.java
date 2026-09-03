package com.jucasoliveira.kitchensink.architecture;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 1.10 — the ArchUnit gate, proven rather than observed.
 *
 * <p>{@link LayeringRulesTest} and {@link BoundedContextRulesTest} have only ever been seen green,
 * and a rule that is green against code which happens to comply is indistinguishable from a rule
 * that matches nothing. The acceptance criterion for 1.10 is that "a slice-shaped test can fail for
 * the right reason", so this class takes the <em>same rule objects</em> the gate runs and evaluates
 * them against {@code architecture.fixtures.leaky}, a package of classes written to break one rule
 * each — and asserts that each rule names the class that broke it.
 *
 * <p>Two things make this safe to keep in the build. The fixtures are test sources, and the gate
 * imports with {@code DoNotIncludeTests}, so they never reach it. And the rules are evaluated, not
 * checked: {@link ArchRule#evaluate} returns the violations instead of throwing, so a rule that
 * bites here is a passing test.
 *
 * <p>The bounded-context rules in {@link BoundedContextRulesTest} are not covered: they match on
 * {@code com.jucasoliveira.kitchensink.<context>..}, one package below the root, and a fixture
 * there would be a class in a real (or a deferred) context. {@code failOnEmptyShould=true} in
 * {@code archunit.properties} is what guards those against matching nothing.
 */
class RulesCanFailTest {

	private static final String FIXTURES = Contexts.ROOT + ".architecture.fixtures.leaky";

	private static final JavaClasses LEAKY = new ClassFileImporter().importPackages(FIXTURES);

	@Test
	@DisplayName("the rule set is not globally exempt from empty should-clauses any more")
	void empty_rules_fail_by_default() {
		// Issue 1.2 set archRule.failOnEmptyShould=false so the rules could land before the code.
		// 1.10 turned it back on; the one rule that still legitimately matches nothing carries its
		// own allowEmptyShould(true) — see LayeringRulesTest.the_jpa_adapter_does_not_know_the_mongo_one.
		assertThat(ArchConfiguration.get().getPropertyOrDefault("archRule.failOnEmptyShould", "true"))
			.isEqualTo("true");
	}

	@Test
	@DisplayName("a Spring Data annotation on an aggregate is caught: the domain is plain Java")
	void the_domain_rule_bites() {
		assertViolated(LayeringRulesTest.the_domain_is_plain_java, "LeakyAggregate",
				"org.springframework.data.mongodb.core.mapping.Document");
	}

	@Test
	@DisplayName("a store type outside the Mongo adapter is caught, wherever it is")
	void the_mongo_containment_rule_bites() {
		assertViolated(LayeringRulesTest.mongodb_types_stay_in_the_mongo_adapter, "LeakyAggregate",
				"org.springframework.data.mongodb");
	}

	@Test
	@DisplayName("an application service naming an adapter type is caught: the port/adapter inversion holds")
	void the_application_rule_bites() {
		assertViolated(LayeringRulesTest.the_application_layer_does_not_know_its_adapters, "LeakyService",
				"LeakyDocument");
	}

	@Test
	@DisplayName("a Repository that is a class is caught: ports are interfaces")
	void the_port_rule_bites() {
		assertViolated(LayeringRulesTest.repository_ports_are_interfaces, "LeakyRepository", "is no interface");
	}

	@Test
	@DisplayName("a controller reaching past the service into the store is caught")
	void the_web_adapter_rule_bites() {
		assertViolated(LayeringRulesTest.the_web_adapter_does_not_reach_into_persistence, "LeakyController",
				"LeakyDocument");
	}

	@Test
	@DisplayName("and a rule the fixtures do not break stays quiet, so the violations above are the rule's, not the fixture's")
	void a_rule_the_fixtures_respect_reports_nothing() {
		// The fixtures never touch jakarta.persistence; if this rule fired on them, the assertions
		// above would be proving that the importer sees the fixtures, not that each rule bites.
		EvaluationResult result = LayeringRulesTest.jpa_types_stay_in_the_jpa_adapter.evaluate(LEAKY);
		assertThat(result.hasViolation()).as(result.getFailureReport().toString()).isFalse();
	}

	/** The rule reports a violation, and the report names both the offender and what it reached for. */
	private static void assertViolated(ArchRule rule, String offender, String reachedFor) {
		EvaluationResult result = rule.evaluate(LEAKY);
		assertThat(result.hasViolation()).as("expected %s to be violated by %s", rule.getDescription(), offender)
			.isTrue();
		assertThat(result.getFailureReport().getDetails())
			.anySatisfy(detail -> assertThat(detail).contains(offender).contains(reachedFor));
	}

}
