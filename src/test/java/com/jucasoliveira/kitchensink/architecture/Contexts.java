package com.jucasoliveira.kitchensink.architecture;

import java.util.Arrays;

/**
 * The bounded contexts of the migration and their tier under
 * <a href="../../../../../../../../docs/adr/0006-deliverable-scope-kitchensink-slice.md">ADR-0006</a>.
 *
 * <p>The four legacy EARs (petstore / opc / supplier / admin) collapse into modules inside one
 * deployable, "with the boundaries enforced by ArchUnit rather than by classloaders" — ADR-0006.
 * This class is the single place that list is written down; the rules in this package are
 * expressed against it.
 *
 * <p>{@link #DEFERRED} is deliberately a list of packages that do <em>not</em> exist. Issue 1.2
 * asks for the dependency rules to be asserted "before there is code to violate them", and
 * {@link BoundedContextRulesTest#deferred_contexts_stay_unbuilt} is what makes that literal: it
 * fails the build the day someone starts T3 without reopening ADR-0006.
 */
final class Contexts {

	/** Root package of the application. */
	static final String ROOT = "com.jucasoliveira.kitchensink";

	/**
	 * Cross-context code: shared value objects and the Spring configuration classes. Every context
	 * may depend on this; it depends on no context.
	 */
	static final String SHARED = ROOT + ".shared";

	/** T1 — the kitchensink slice. The deliverable (ADR-0006). */
	static final String CUSTOMER = "customer";

	/** T2 — the catalog read path. Should ship (ADR-0006). */
	static final String CATALOG = "catalog";

	/** Contexts that are being built: T1 + T2. */
	static final String[] BUILT = { CUSTOMER, CATALOG };

	/**
	 * T3 — cart, checkout, order workflow, approval, supplier. Designed in ADR-0004 and
	 * {@code docs/01-legacy-architecture.md} §5, and deliberately <em>not built</em> (ADR-0006).
	 * The issues are closed in the "Deferred — designed, not built" milestone.
	 */
	static final String[] DEFERRED = { "cart", "order", "opc", "supplier", "admin" };

	/** Turns context names into the {@code com.jucasoliveira.kitchensink.x..} patterns ArchUnit matches on. */
	static String[] packagesOf(String... contexts) {
		return Arrays.stream(contexts).map(context -> ROOT + "." + context + "..").toArray(String[]::new);
	}

	private Contexts() {
	}

}
