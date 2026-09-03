package com.jucasoliveira.kitchensink.catalog.adapter.persistence;

import java.util.List;

import com.jucasoliveira.kitchensink.catalog.LegacyCatalogSeed;
import com.jucasoliveira.kitchensink.catalog.application.CatalogRepository;
import com.jucasoliveira.kitchensink.catalog.domain.Category;
import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.ItemDetails;
import com.jucasoliveira.kitchensink.catalog.domain.Product;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 3.2 and 3.3 — the six read statements of {@code CatalogDAOSQL.xml}, through the port,
 * against <em>whichever</em> adapter the active profile resolves it to.
 *
 * <p>Legacy anchor: {@code apps/petstore/src/docroot/CatalogDAOSQL.xml:63-127} — seven statements,
 * written twice (cloudscape at {@code :63}, oracle at {@code :129}) because the DAO had to be
 * hand-ported per dialect ({@code CatalogDAOFactory.java}), and executed by
 * {@code GenericCatalogDAO.java}. {@code SEARCH_ITEMS}, the seventh, is pinned separately by
 * {@link CatalogSearchContract}.
 *
 * <p>This class holds the assertions and nothing else. The two subclasses supply a profile and a
 * store: {@code MongoCatalogRepositoryTest} boots a {@code mongo:7.0} container,
 * {@code JpaCatalogRepositoryTest} boots H2 under {@code --spring.profiles.active=jpa} and imports
 * no Testcontainers configuration at all. That is issue 3.3's acceptance criterion made literal —
 * "the same port test suite passes under the jpa profile" is one class run twice, not two classes
 * that happen to agree.
 *
 * <p>{@code CatalogDAOFactory.java:58} is the legacy's version of the same idea and the reason 3.3
 * exists: the 2003 app chose {@code CloudscapeCatalogDAO} or {@code OracleCatalogDAO} from a JNDI
 * env-entry, and every statement was written twice by hand because the DAO leaked its dialect. Here
 * the choice is a Spring profile, the port is one interface, and the test suite is one file.
 *
 * <p>The test speaks to {@link CatalogRepository} and never to an adapter, and it boots the real
 * application context rather than a {@code @DataMongoTest} / {@code @DataJpaTest} slice — same
 * reasoning as {@code CustomerMongoRoundTripTest}: what is being proven is that the running
 * application resolves the port to the right adapter under the profile it was started with.
 *
 * <p>Every expected value comes from {@link LegacyCatalogSeed}, which reads a verbatim copy of
 * {@code Populate-UTF8.xml} — never a literal typed here. So a mapping that silently dropped a
 * locale row, or a fixture that drifted from the legacy file, fails here rather than in a demo.
 *
 * <p>Tagged {@code parity}: a red run means the migrated read path no longer answers what Pet
 * Store's DAO answered.
 *
 * <h2>Two deviations these tests pin on purpose</h2>
 *
 * <ol>
 * <li><b>No {@code Locale} parameter.</b> Every legacy statement took one
 * ({@code where locale = ?}) because the localized row lived in a second table and one query
 * could fetch one locale. Since 3.1 put {@code Map<locale, details>} inside the aggregate, one
 * read returns them all and choosing between them is 3.4's job. Note what that costs the JPA
 * adapter, which still has those tables: it reads the whole {@code *_details} collection where
 * the legacy read one row of it.</li>
 * <li><b>No {@code order by name}.</b> {@code GET_CATEGORIES:71} and {@code GET_PRODUCTS:85} sort
 * in the database. {@code name} is locale-scoped, so sorting in the store would mean sorting on
 * {@code details.<locale>.name} under a Mongo collation whose ordering for {@code ja_JP} and
 * {@code zh_CN} would not match Cloudscape's anyway — and would mean two different orderings
 * between the two adapters, which is worse. The sort moves to the service, which is why the
 * assertions below are order-insensitive.</li>
 * </ol>
 */
@Tag("parity")
public abstract class CatalogRepositoryContract {

	static final String LOCALE = "en_US";

	protected static LegacyCatalogSeed legacy;

	@Autowired
	protected CatalogRepository catalog;

	@BeforeAll
	protected static void readTheLegacySeed() {
		legacy = LegacyCatalogSeed.read();
	}

	@Test
	@DisplayName("the port is bound to the adapter of the active profile, and to nothing else")
	protected void the_port_resolves_to_the_adapter_of_this_profile() {
		// By package, not by class name: the rule LayeringRulesTest states is "the adapter lives in
		// the mongo package" / "in the jpa package", not "the adapter is called X". The subclass
		// sits in the package its adapter should be in, so getClass() is the expectation.
		// Unwrapped in case @Repository's exception-translation proxy sits in front of the bean.
		Class<?> adapter = AopProxyUtils.ultimateTargetClass(this.catalog);
		assertThat(adapter.getPackageName()).isEqualTo(getClass().getPackageName());
	}

	@Test
	@DisplayName("GET_CATEGORY (CatalogDAOSQL.xml:64) — FISH comes back with all three category_details rows in one read")
	protected void get_category() {
		LegacyCatalogSeed.Category fish = legacyCategory("FISH");

		Category category = this.catalog.findCategory("FISH").orElseThrow();

		assertThat(category.id()).isEqualTo(fish.id());
		assertThat(category.details().keySet()).containsExactlyInAnyOrderElementsOf(fish.details().keySet());
		fish.details().forEach((locale, expected) -> {
			assertThat(category.details().get(locale).name()).as("category_details.name for %s", locale)
					.isEqualTo(expected.name());
			assertThat(category.details().get(locale).image()).as("category_details.image for %s", locale)
					.isEqualTo(expected.image());
		});
	}

	@Test
	@DisplayName("category_details.descn — the legacy seed carries no category description, and neither adapter invents one")
	protected void a_category_description_is_null_because_the_seed_has_none() {
		// Category.dtd has no Description element, so every category_details.descn row was empty.
		// Asserted rather than ignored: a null here is fidelity, and it should stay a decision
		// rather than look like a mapping gap when 3.5 renders the sidebar. Worth running against
		// both stores — a missing key in a document and a NULL column are not the same absence,
		// and the port has to make them look the same.
		Category category = this.catalog.findCategory("FISH").orElseThrow();

		assertThat(legacyCategory("FISH").details().get(LOCALE).description()).isNull();
		assertThat(category.details().get(LOCALE).description()).isNull();
	}

	@Test
	@DisplayName("GET_CATEGORIES (CatalogDAOSQL.xml:71) — the same five categories; 'order by name' is the service's job, not the store's")
	protected void get_categories() {
		List<Category> categories = this.catalog.findAllCategories();

		assertThat(categories).extracting(Category::id).containsExactlyInAnyOrderElementsOf(
				legacy.categories.stream().map(LegacyCatalogSeed.Category::id).toList());
		assertThat(categories).allSatisfy(category -> assertThat(category.details()).isNotEmpty());
	}

	@Test
	@DisplayName("GET_PRODUCT (CatalogDAOSQL.xml:78) — FI-SW-01 keeps its category reference and every product_details row")
	protected void get_product() {
		LegacyCatalogSeed.Product angelfish = legacyProduct("FI-SW-01");

		Product product = this.catalog.findProduct("FI-SW-01").orElseThrow();

		assertThat(product.id()).isEqualTo(angelfish.id());
		assertThat(product.categoryId()).isEqualTo(angelfish.categoryId());
		assertThat(product.details().keySet()).containsExactlyInAnyOrderElementsOf(angelfish.details().keySet());
		angelfish.details().forEach((locale, expected) -> {
			assertThat(product.details().get(locale).name()).as("product_details.name for %s", locale)
					.isEqualTo(expected.name());
			assertThat(product.details().get(locale).description()).as("product_details.descn for %s", locale)
					.isEqualTo(expected.description());
		});
	}

	@Test
	@DisplayName("GET_PRODUCTS (CatalogDAOSQL.xml:85) — the FISH category holds exactly the legacy seed's FISH products")
	protected void get_products_in_category() {
		List<Product> products = this.catalog.findProductsInCategory("FISH");

		assertThat(products).extracting(Product::id).containsExactlyInAnyOrderElementsOf(
				legacy.products.stream().filter(p -> p.categoryId().equals("FISH"))
						.map(LegacyCatalogSeed.Product::id).toList());
		assertThat(products).extracting(Product::categoryId).containsOnly("FISH");
	}

	@Test
	@DisplayName("GET_ITEM (CatalogDAOSQL.xml:92) — one findById, and the four-table locale-correlated join is gone")
	protected void get_item_is_one_find_by_id() {
		LegacyCatalogSeed.Item est1 = legacyItem("EST-1");

		Item item = this.catalog.findItem("EST-1").orElseThrow();

		// GET_ITEM needed item ⋈ item_details ⋈ product_details ⋈ product, with b.locale = c.locale
		// restated as a join predicate, and it still returned one locale per execution. This one
		// call returns every locale the seed holds for EST-1. Under jpa the item_details table is
		// still there — what is gone is the join, not the table.
		assertThat(item.id()).isEqualTo(est1.id());
		assertThat(item.productId()).isEqualTo(est1.productId());
		assertThat(item.details().keySet()).containsExactlyInAnyOrderElementsOf(est1.details().keySet());
		est1.details().forEach((locale, expected) -> {
			ItemDetails row = item.details().get(locale);
			assertThat(row.listPrice()).as("item_details.listprice for %s", locale).isEqualTo(expected.listPrice());
			assertThat(row.unitCost()).as("item_details.unitcost for %s", locale).isEqualTo(expected.unitCost());
			assertThat(row.attributes()).as("attr1..attr5 for %s", locale).isEqualTo(expected.attributes());
			assertThat(row.image()).as("item_details.image for %s", locale).isEqualTo(expected.image());
			assertThat(row.description()).as("item_details.descn for %s", locale).isEqualTo(expected.description());
		});
	}

	@Test
	@DisplayName("GET_ITEM's projection — catid and the product name now cost a second read, and that is the whole trade")
	protected void the_product_page_projection_takes_two_reads() {
		// GET_ITEM projected (catid, a.productid, name, b.image, b.descn, attr1..attr5, listprice,
		// unitcost): item columns and product columns flattened into one row by the join. The port
		// returns aggregates instead, so 3.4 assembles the same projection from two reads. Written
		// down as a test because it is the honest half of "the six-way join is gone": four of the
		// six predicates disappear with the details tables, and the item→product association
		// becomes a second findById rather than a join.
		Item item = this.catalog.findItem("EST-1").orElseThrow();
		Product product = this.catalog.findProduct(item.productId()).orElseThrow();

		LegacyCatalogSeed.Item est1 = legacyItem("EST-1");
		LegacyCatalogSeed.Product angelfish = legacyProduct(est1.productId());

		assertThat(product.categoryId()).isEqualTo(angelfish.categoryId());
		assertThat(product.details().get(LOCALE).name()).isEqualTo(angelfish.details().get(LOCALE).name());
		assertThat(item.details().get(LOCALE).description()).isEqualTo(est1.details().get(LOCALE).description());
		assertThat(this.catalog.findCategory(product.categoryId())).isPresent();
	}

	@Test
	@DisplayName("GET_ITEMS (CatalogDAOSQL.xml:102) — FI-SW-01 has exactly the legacy seed's items")
	protected void get_items_for_product() {
		List<Item> items = this.catalog.findItemsForProduct("FI-SW-01");

		assertThat(items).extracting(Item::id).containsExactlyInAnyOrderElementsOf(
				legacy.items.stream().filter(i -> i.productId().equals("FI-SW-01"))
						.map(LegacyCatalogSeed.Item::id).toList());
		assertThat(items).extracting(Item::productId).containsOnly("FI-SW-01");
	}

	@Test
	@DisplayName("Item.dtd (ItemDetails+) — EST-15 (Populate-UTF8.xml:882) still has no ja_JP row after the round trip")
	protected void a_missing_locale_row_stays_missing() {
		// The gap CatalogSeedLoadTest found in the store, and CatalogDomainLegacyFidelityTest
		// pinned in the domain, must survive the mapper too: nothing fills it in, and details()
		// simply lacks the key. Under ja_JP the legacy join dropped EST-15 from the result set
		// entirely; here it loads and 3.4 decides what to show.
		Item item = this.catalog.findItem("EST-15").orElseThrow();

		assertThat(legacyItem("EST-15").details()).doesNotContainKey("ja_JP");
		assertThat(item.details()).doesNotContainKey("ja_JP");
		assertThat(item.details().keySet()).containsExactlyInAnyOrder("en_US", "zh_CN");
	}

	@Test
	@DisplayName("GenericCatalogDAO.java:174,227,294 — a miss was 'return null'; the port makes it Optional.empty()")
	protected void a_miss_is_an_empty_optional() {
		assertThat(this.catalog.findCategory("NO-SUCH-CATEGORY")).isEmpty();
		assertThat(this.catalog.findProduct("NO-SUCH-PRODUCT")).isEmpty();
		assertThat(this.catalog.findItem("NO-SUCH-ITEM")).isEmpty();
	}

	@Test
	@DisplayName("GenericCatalogDAO.java:204,258 — an empty result was Page.EMPTY_PAGE, and stays an empty list")
	protected void an_empty_result_is_an_empty_list() {
		assertThat(this.catalog.findProductsInCategory("NO-SUCH-CATEGORY")).isEmpty();
		assertThat(this.catalog.findItemsForProduct("NO-SUCH-PRODUCT")).isEmpty();
	}

	@Test
	@DisplayName("the whole seeded catalog is reachable through the port: 5 categories, 16 products, 28 items")
	protected void every_seeded_document_is_reachable() {
		// Reads the graph the way 3.5's screens will walk it — category → products → items — and
		// so catches an adapter that resolves one entity but not its children.
		assertThat(this.catalog.findAllCategories()).hasSize(legacy.categories.size());

		List<Product> products = this.catalog.findAllCategories().stream()
				.flatMap(c -> this.catalog.findProductsInCategory(c.id()).stream()).toList();
		assertThat(products).hasSize(legacy.products.size());

		List<Item> items = products.stream()
				.flatMap(p -> this.catalog.findItemsForProduct(p.id()).stream()).toList();
		assertThat(items).hasSize(legacy.items.size());
	}

	private static LegacyCatalogSeed.Category legacyCategory(String id) {
		return legacy.categories.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
	}

	private static LegacyCatalogSeed.Product legacyProduct(String id) {
		return legacy.products.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
	}

	private static LegacyCatalogSeed.Item legacyItem(String id) {
		return legacy.items.stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow();
	}

}
