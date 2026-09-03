package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import com.jucasoliveira.kitchensink.catalog.adapter.persistence.CatalogSearchContract;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Issue 3.2 — {@link CatalogSearchContract} against the Mongo adapter's {@code $lookup} +
 * {@code $regex} aggregation, under the default profile.
 *
 * <p>Same context and same container as {@link MongoCatalogRepositoryTest}: identical properties,
 * identical imports, so Spring's test context cache hands both classes the one seeded store.
 */
@Tag("parity")
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@Import(TestcontainersConfiguration.class)
class MongoCatalogSearchTest extends CatalogSearchContract {

}
