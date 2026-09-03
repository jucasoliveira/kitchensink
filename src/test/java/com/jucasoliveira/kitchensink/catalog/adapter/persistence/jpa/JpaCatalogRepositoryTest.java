package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import com.jucasoliveira.kitchensink.catalog.adapter.persistence.CatalogRepositoryContract;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Issue 3.3, the acceptance criterion: "the same port test suite passes under the {@code jpa}
 * profile".
 *
 * <p>This is {@link CatalogRepositoryContract} — the identical assertions
 * {@code MongoCatalogRepositoryTest} runs — pointed at H2 by one annotation. Not a copy of them:
 * a copy would drift, and a suite that drifted would let the two adapters answer differently
 * while both stayed green, which is the exact failure the profile switch is supposed to rule out.
 *
 * <p>Note what is <em>absent</em>: {@code @Import(TestcontainersConfiguration.class)}. Under
 * {@code jpa} there is no MongoDB, no Docker daemon and no container to wait for — the same point
 * {@code PersistenceProfileJpaTest} makes about the application context, made here about the
 * catalog suite. If this class ever needs a Mongo container to go green, the switch has stopped
 * being a switch.
 *
 * <p>Legacy anchor for the issue as a whole: {@code CatalogDAOFactory.java:58}. Pet Store also had
 * two catalog implementations behind one interface — {@code CloudscapeCatalogDAO} and
 * {@code OracleCatalogDAO}, chosen from the {@code param/CatalogDAOClass} JNDI env-entry
 * ({@code ejb-jar.xml:58}) — but every statement was written out twice by hand
 * ({@code CatalogDAOSQL.xml:63} and {@code :129}) and neither implementation had a test. The
 * migrated equivalent is one port, two adapters, and one suite that runs against both.
 *
 * <h2>Why the datasource URL is overridden</h2>
 *
 * <p>{@code jdbc:h2:mem:kitchensink;DB_CLOSE_DELAY=-1} — the shipped default that
 * {@code ProfileConfigurationTest} pins — names one in-memory database per JVM, not per
 * application context. Surefire runs every {@code jpa} test class in one JVM, Spring caches a
 * context per distinct configuration, and {@code ddl-auto=create-drop} rebuilds the schema each
 * time a new one starts. So a second {@code jpa} context booting between two classes that share
 * this one would silently empty the seeded tables underneath them. A separate database name keeps
 * the seeded contexts isolated from {@code PersistenceProfileJpaTest}, which asserts on the
 * shipped URL and must keep it.
 */
@Tag("parity")
@SpringBootTest(properties = { "kitchensink.seed.catalog=true",
		"spring.datasource.url=jdbc:h2:mem:catalog-parity;DB_CLOSE_DELAY=-1" })
@ActiveProfiles("jpa")
class JpaCatalogRepositoryTest extends CatalogRepositoryContract {

}
