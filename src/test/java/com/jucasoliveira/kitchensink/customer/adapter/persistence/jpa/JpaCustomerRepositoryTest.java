package com.jucasoliveira.kitchensink.customer.adapter.persistence.jpa;

import com.jucasoliveira.kitchensink.customer.adapter.persistence.CustomerRepositoryContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Issue 4.6, the acceptance criterion: "both profiles green".
 *
 * <p>This is {@link CustomerRepositoryContract} — the identical 15 assertions
 * {@code CustomerMongoRoundTripTest} runs — pointed at H2 by two annotations. Not a copy of them:
 * a copy would drift, and a suite that drifted would let the two adapters answer differently while
 * both stayed green, which is the exact failure the profile switch is supposed to rule out. The
 * catalog made the same move in issue 3.3 ({@code JpaCatalogRepositoryTest}).
 *
 * <p>Note what is <em>absent</em>: {@code @Import(TestcontainersConfiguration.class)}. Under
 * {@code jpa} there is no MongoDB, no Docker daemon and no container to wait for. If this class
 * ever needs a Mongo container to go green, the switch has stopped being a switch.
 *
 * <p>The contract's first assertion is that the port resolves to an adapter in <em>this</em>
 * class's package, which is what makes the pair of subclasses meaningful: under {@code mongo} it
 * must be the Mongo adapter, under {@code jpa} the JPA one, and a profile that quietly resolved to
 * the wrong store would fail here rather than pass twice.
 *
 * <h2>Why the datasource URL is overridden</h2>
 *
 * <p>Same reason as {@code JpaCatalogRepositoryTest}: {@code jdbc:h2:mem:kitchensink} names one
 * in-memory database per JVM, not per application context, and {@code ddl-auto=create-drop}
 * rebuilds the schema whenever a new context starts. A separate database name keeps this class
 * isolated from {@code PersistenceProfileJpaTest}, which asserts on the shipped URL and must keep
 * it.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:customer-contract;DB_CLOSE_DELAY=-1")
@ActiveProfiles("jpa")
class JpaCustomerRepositoryTest extends CustomerRepositoryContract {

	@Autowired
	JdbcTemplate jdbc;

	/**
	 * The Mongo twin drops a collection; here the rows go.
	 *
	 * <p>Deliberately <em>not</em> {@code @Transactional} on the class. A test-managed transaction
	 * would wrap every assertion in one persistence context, so the contract's tests would read
	 * their own writes out of Hibernate's first-level cache rather than out of H2 — and
	 * {@code add()} returning a managed instance would satisfy
	 * {@link CustomerRepositoryContract#add_returns_what_was_stored()} without the row ever having
	 * been written. Each port call therefore opens and commits its own transaction, exactly as it
	 * does in the running application, and the teardown goes around JPA entirely.
	 */
	@Override
	protected void clearStore() {
		this.jdbc.execute("delete from customer");
	}

}
