package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import com.jucasoliveira.kitchensink.catalog.adapter.persistence.CatalogSearchContract;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Issue 3.3 — {@link CatalogSearchContract} against the JPA adapter, which is the one place in the
 * slice where the migration hands a statement <em>back</em> to SQL.
 *
 * <p>{@code SEARCH_ITEMS} ({@code CatalogDAOSQL.xml:112-127}) is assembled per keyword here almost
 * exactly as {@code GenericCatalogDAO.java:343-395} assembled it from the
 * {@code occurrence="VARIABLE"} fragment, and {@code key(idet) = :locale and key(pdet) = :locale}
 * is the legacy's {@code b.locale = ? and b.locale = c.locale}. The interesting consequence is
 * that {@code search_is_scoped_to_one_locale} — EST-15 dropping out of a {@code ja_JP} search
 * because it has no {@code ja_JP} item_details row — costs the adapter no code at all: the join
 * predicate excludes it, exactly as it did in 2003. The Mongo adapter has to arrange the same
 * outcome deliberately, with two {@code $match} stages around a {@code $lookup}.
 *
 * <p>Same properties as {@link JpaCatalogRepositoryTest}, so both share one context and one
 * seeded H2 database.
 */
@Tag("parity")
@SpringBootTest(properties = { "kitchensink.seed.catalog=true",
		"spring.datasource.url=jdbc:h2:mem:catalog-parity;DB_CLOSE_DELAY=-1" })
@ActiveProfiles("jpa")
class JpaCatalogSearchTest extends CatalogSearchContract {

}
