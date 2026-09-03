package com.jucasoliveira.kitchensink.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Issue 1.2 — the layering inside a bounded context: {@code domain} / {@code application} /
 * {@code adapter.{web,persistence.{mongo,jpa}}}.
 *
 * <p>This is ADR-0005 made executable. That ADR commits to three things these rules police:
 * domain aggregates are "plain Java (records/classes, no persistence annotations)", each aggregate
 * has "a repository port in the application layer", and "two adapters implement the ports". The
 * profile-switch demo — same suite, same golden path, Mongo or JPA — only works if neither adapter
 * has leaked upwards, and a leak is the kind of thing that is cheap to prevent and expensive to
 * unpick on day four.
 */
@AnalyzeClasses(packages = Contexts.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringRulesTest {

	private static final String DOMAIN = "..domain..";

	private static final String APPLICATION = "..application..";

	private static final String ADAPTER = "..adapter..";

	private static final String WEB_ADAPTER = "..adapter.web..";

	private static final String PERSISTENCE_ADAPTER = "..adapter.persistence..";

	private static final String MONGO_ADAPTER = "..adapter.persistence.mongo..";

	private static final String JPA_ADAPTER = "..adapter.persistence.jpa..";

	/**
	 * The 1.1 skeleton spike ({@code Member}, {@code MemberRepository}, {@code HomeController})
	 * sits in the root package and is deliberately framework-soaked — it exists to prove the stack
	 * carries, and {@code SkeletonSpikeTest} is the evidence for it. It is throwaway and E4
	 * replaces it. Matching the package exactly, with no trailing "..", excludes those classes and
	 * nothing else. ({@code SecurityConfig} started here too; Issue 1.8 moved it to
	 * {@code shared.security}, where the exemptions below already expected it.)
	 */
	private static final String SKELETON_SPIKE = Contexts.ROOT;

	/**
	 * The domain is plain Java. ADR-0005 §1; AGENTS.md §5 states the narrow form of this
	 * ("the domain package imports no Spring Data type") as a merge condition.
	 *
	 * <p>{@code jakarta.validation} is the one deliberate exception, and it is not a loophole:
	 * ADR-0006 makes "Jakarta Validation replaces hand-rolled checks" a headline of the slice, and
	 * the constraints belong on the aggregate they constrain rather than on a DTO beside it. It is
	 * a specification API with no runtime attached, which is exactly what the rest of this list
	 * is not.
	 */
	@ArchTest
	static final ArchRule the_domain_is_plain_java = noClasses().that()
		.resideInAPackage(DOMAIN)
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..", "jakarta.persistence..", "jakarta.servlet..",
				"org.bson..", "com.mongodb..", "org.thymeleaf..")
		.because("ADR-0005 §1: domain aggregates are plain Java with no persistence annotations, "
				+ "which is what lets one aggregate carry both a Mongo and a JPA mapping");

	/** The domain is the innermost layer, so it points at nothing above it. */
	@ArchTest
	static final ArchRule the_domain_depends_on_no_outer_layer = noClasses().that()
		.resideInAPackage(DOMAIN)
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage(APPLICATION, ADAPTER)
		.because("dependencies point inwards: domain <- application <- adapter");

	/**
	 * The port/adapter inversion, stated. The application layer owns the repository interface; the
	 * adapters implement it and the application never names one. This is the rule the
	 * {@code mongo}/{@code jpa} profile switch rests on.
	 */
	@ArchTest
	static final ArchRule the_application_layer_does_not_know_its_adapters = noClasses().that()
		.resideInAPackage(APPLICATION)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(ADAPTER)
		.because("ADR-0005 §2-4: the port lives in the application layer and the adapter is chosen "
				+ "by profile at runtime, not by an import at compile time");

	/** A port is an interface, or it is not a port. */
	@ArchTest
	static final ArchRule repository_ports_are_interfaces = classes().that()
		.resideInAPackage(APPLICATION)
		.and()
		.haveSimpleNameEndingWith("Repository")
		.should()
		.beInterfaces()
		.because("ADR-0005 §2: each aggregate has a repository port, and a port with an "
				+ "implementation inside it cannot have two adapters");

	/** The web tier goes through the application layer, never straight at a store. */
	@ArchTest
	static final ArchRule the_web_adapter_does_not_reach_into_persistence = noClasses().that()
		.resideInAPackage(WEB_ADAPTER)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(PERSISTENCE_ADAPTER)
		.because("both the Thymeleaf screens and the REST resource (ADR-0003) sit behind the same "
				+ "application services, so neither can be store-specific");

	/**
	 * The two adapters are alternatives, not collaborators. If one imports the other the
	 * profile-switch demo is a fiction: running under {@code jpa} would still drag Mongo in.
	 */
	@ArchTest
	static final ArchRule the_mongo_adapter_does_not_know_the_jpa_one = noClasses().that()
		.resideInAPackage(MONGO_ADAPTER)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(JPA_ADAPTER)
		.because("ADR-0005 §3-4: two adapters behind one port, selected by profile");

	/**
	 * Symmetric to the above; stated separately so a failure names the direction that broke.
	 *
	 * <p>This rule used to carry {@code allowEmptyShould(true)}, and that exemption was always
	 * meant to be temporary: issue 1.10 turned {@code failOnEmptyShould} back on
	 * ({@code src/test/resources/archunit.properties}) at a point when no
	 * {@code adapter.persistence.jpa} package existed for the that-clause to match. Issue 3.3
	 * wrote that package, so the exemption is gone as the comment there instructed. From here on
	 * an empty match means what it should mean - the JPA adapter was renamed or moved and this
	 * rule stopped guarding anything. (4.6 adds the customer half; this rule already covers it.)
	 */
	@ArchTest
	static final ArchRule the_jpa_adapter_does_not_know_the_mongo_one = noClasses().that()
		.resideInAPackage(JPA_ADAPTER)
		.should()
		.dependOnClassesThat()
		.resideInAPackage(MONGO_ADAPTER)
		.because("ADR-0005 §3-4: two adapters behind one port, selected by profile");

	/**
	 * Store types stay in their adapter. This is the rule that keeps {@code @Document} and the
	 * Mongo driver from creeping up into a service "just for one query" — the usual way a
	 * store-agnostic port quietly stops being one.
	 */
	@ArchTest
	static final ArchRule mongodb_types_stay_in_the_mongo_adapter = noClasses().that()
		.resideOutsideOfPackages(MONGO_ADAPTER, SKELETON_SPIKE)
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework.data.mongodb..", "com.mongodb..", "org.bson..")
		.because("the Mongo mapping is an adapter concern; see ADR-0005 on the customers document");

	/** Same rule, other adapter. */
	@ArchTest
	static final ArchRule jpa_types_stay_in_the_jpa_adapter = noClasses().that()
		.resideOutsideOfPackages(JPA_ADAPTER, SKELETON_SPIKE)
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("jakarta.persistence..", "org.springframework.data.jpa..")
		.because("the relational mapping is an adapter concern, symmetric with the Mongo one");

	/**
	 * Hashing happens in exactly one place. Issue 1.8 replaces {@code UserEJB.java:88}'s plaintext
	 * {@code equals} with BCrypt, and the guarantee is only worth something if there is one code
	 * path that produces a hash: the application service. The web adapter of 1.9 binds a form and
	 * hands the raw password to that service; the security adapter hands the stored hash to Spring
	 * Security; the {@code PasswordEncoder} bean itself is declared in {@code shared.security}.
	 * Nothing else has any business importing the crypto package.
	 */
	@ArchTest
	static final ArchRule password_hashing_happens_in_the_application_layer = noClasses().that()
		.resideOutsideOfPackages(APPLICATION, Contexts.SHARED + "..", SKELETON_SPIKE)
		.should()
		.dependOnClassesThat()
		.resideInAPackage("org.springframework.security.crypto..")
		.because("Issue 1.8: one hashing path, in the application service, behind the PasswordEncoder "
				+ "bean that shared.security declares (finding #1)");

	/**
	 * The web tier is a delivery mechanism and lives in one place. The legacy app scattered its
	 * equivalent across WAF {@code HTMLAction}s and screenflow XML — see
	 * {@code docs/01-legacy-architecture.md} — which is precisely why "where is the web tier?"
	 * was a hard question to answer about it.
	 */
	@ArchTest
	static final ArchRule the_web_tier_lives_in_the_web_adapter = noClasses().that()
		.resideOutsideOfPackages(WEB_ADAPTER, Contexts.SHARED + "..", SKELETON_SPIKE)
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
		.because("controllers and REST resources belong to the web adapter; the shared package is "
				+ "exempt because the security filter chain is configured there");

}
