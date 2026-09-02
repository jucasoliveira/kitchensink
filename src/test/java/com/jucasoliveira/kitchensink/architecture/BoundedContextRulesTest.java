package com.jucasoliveira.kitchensink.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Issue 1.2 — the boundaries between bounded contexts.
 *
 * <p>The legacy application enforced these boundaries with four EARs and four classloaders.
 * ADR-0006 collapses them into one deployable and moves the enforcement here, on the argument that
 * the split was "a J2EE packaging artefact, not a scaling decision" — so the seams have to stay
 * visible some other way, or splitting them back out stops being a packaging change.
 *
 * <p>These rules are asserted while the packages are still empty, which is the point of the issue:
 * they are in place before there is code to violate them. See {@code src/test/resources/archunit.properties}.
 */
@AnalyzeClasses(packages = Contexts.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class BoundedContextRulesTest {

	/**
	 * The four-EAR boundary, restated. A context talks to another context through no import at all
	 * — cross-context collaboration, when T3 is ever built, goes through the shared package or an
	 * application event (ADR-0004), never a direct type reference.
	 */
	@ArchTest
	static final ArchRule contexts_do_not_depend_on_each_other = slices()
		.matching(Contexts.ROOT + ".(*)..")
		.namingSlices("context '$1'")
		.should()
		.notDependOnEachOther()
		.ignoreDependency(DescribedPredicate.alwaysTrue(), JavaClass.Predicates.resideInAPackage(Contexts.SHARED + ".."))
		.because("ADR-0006 collapses the four EARs into modules in one deployable; the seams are "
				+ "enforced here instead of by classloaders");

	/**
	 * T3 is designed, not built. If a class ever appears under cart / order / opc / supplier /
	 * admin, this fails — which is the intended signal, not an inconvenience: reopening T3 is an
	 * ADR-0006 decision, and this rule is where that decision gets noticed.
	 */
	@ArchTest
	static final ArchRule deferred_contexts_stay_unbuilt = noClasses().should()
		.resideInAnyPackage(Contexts.packagesOf(Contexts.DEFERRED))
		.because("ADR-0006 defers cart, checkout, the order workflow, approval and supplier unbuilt; "
				+ "the issues are closed in the 'Deferred - designed, not built' milestone");

	/** Corollary of the above: nothing in T1 or T2 may reach for a deferred context either. */
	@ArchTest
	static final ArchRule nothing_depends_on_a_deferred_context = noClasses()
		.that()
		.resideOutsideOfPackages(Contexts.packagesOf(Contexts.DEFERRED))
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage(Contexts.packagesOf(Contexts.DEFERRED))
		.because("the kitchensink slice must not acquire a dependency on work ADR-0006 did not build");

	/**
	 * The shared package is shared, which means it is downstream of nothing. The moment it imports
	 * a context it stops being a neutral base and becomes a fifth EAR by accident.
	 */
	@ArchTest
	static final ArchRule shared_depends_on_no_context = noClasses().that()
		.resideInAPackage(Contexts.SHARED + "..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage(Contexts.packagesOf(Contexts.BUILT))
		.because("shared code is a base for every context and must stay independent of all of them");

	/** No cycles between contexts. A cycle is a boundary that has already failed. */
	@ArchTest
	static final ArchRule contexts_are_free_of_cycles = slices().matching(Contexts.ROOT + ".(*)..")
		.namingSlices("context '$1'")
		.should()
		.beFreeOfCycles();

}
