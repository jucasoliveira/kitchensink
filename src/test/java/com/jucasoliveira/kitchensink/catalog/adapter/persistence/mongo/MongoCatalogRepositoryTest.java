package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import com.jucasoliveira.kitchensink.catalog.adapter.persistence.CatalogRepositoryContract;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Issue 3.2 — {@link CatalogRepositoryContract} against the Mongo adapter, under the default
 * profile.
 *
 * <p>Every assertion lives in the contract; this class contributes a store and nothing else. It
 * activates no profile on purpose — ADR-0005 §4 makes {@code mongo} the default, and the way to
 * test a default is to not name it (same reasoning as {@code PersistenceProfileMongoTest}).
 *
 * <p>{@code kitchensink.seed.catalog=true} and the {@code @Import} match {@link CatalogSeedLoadTest}
 * and {@link MongoCatalogSearchTest} exactly, so all three share one context and one container.
 *
 * <p>The contract's first test asserts the port resolves to an adapter in <em>this</em> class's
 * package, which is what makes the pair of subclasses meaningful: run under {@code mongo} it must
 * be the Mongo adapter, run under {@code jpa} it must be the JPA one, and a profile that quietly
 * resolved to the wrong store would fail here rather than pass twice.
 */
@Tag("parity")
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@Import(TestcontainersConfiguration.class)
class MongoCatalogRepositoryTest extends CatalogRepositoryContract {

}
