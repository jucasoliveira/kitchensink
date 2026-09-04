package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import java.util.List;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 7.3, the acceptance criterion: "query plans checked, not assumed".
 *
 * <p>An index that exists is not the same claim as an index the planner chooses, and the two come
 * apart easily — a field name that drifted, an index on {@code categoryId} against a query on
 * {@code category_id}, a collection small enough that the difference never shows in a timing.
 * So every assertion below reads {@code explain()} and looks at the winning plan.
 *
 * <p>The interesting half is {@link #search_is_a_collection_scan_and_that_is_not_a_regression()}.
 * Writing down which query is <em>not</em> served by an index, and why no index could serve it, is
 * worth more than the two that are — it is the difference between a migration that measured and
 * one that assumed.
 *
 * <p>Reads the plan out of {@code queryPlanner.winningPlan} and matches on the stringified
 * subtree rather than navigating to {@code stage}: MongoDB's slot-based engine nests the classic
 * plan under {@code queryPlan} and adds {@code slotBasedPlan} beside it, so the path differs
 * between execution engines while the stage names do not. Deliberately not {@code rejectedPlans} —
 * a COLLSCAN there is normal and asserting on it would be asserting on nothing.
 */
@SpringBootTest(properties = "kitchensink.seed.catalog=true")
@Import(TestcontainersConfiguration.class)
class MongoCatalogIndexTest {

	@Autowired
	MongoTemplate mongo;

	@Test
	@DisplayName("CatalogIndexes declares exactly the two it says it does, beside the free _id index")
	void the_declared_indexes_exist() {
		// Named by their key rather than by Mongo's generated name, because the name is what
		// changes silently when a field is renamed and the key is what the planner matches on.
		assertThat(keysOf("products")).containsExactlyInAnyOrder("{\"_id\": 1}", "{\"categoryId\": 1}");
		assertThat(keysOf("items")).containsExactlyInAnyOrder("{\"_id\": 1}", "{\"productId\": 1}");

		// Categories get nothing beyond _id: findAllCategories reads all five documents and an
		// index would be a write cost with no read to pay for it.
		assertThat(keysOf("categories")).containsExactly("{\"_id\": 1}");
	}

	@Test
	@DisplayName("GET_PRODUCTS (CatalogDAOSQL.xml:85) — findProductsInCategory is an IXSCAN, not a scan of sixteen")
	void products_in_a_category_use_the_index() {
		String plan = winningPlan("products", new Document("categoryId", "FISH"));

		assertThat(plan).contains("IXSCAN").contains("categoryId");
		assertThat(plan).doesNotContain("COLLSCAN");
	}

	@Test
	@DisplayName("GET_ITEMS (CatalogDAOSQL.xml:102) — findItemsForProduct is an IXSCAN too")
	void items_for_a_product_use_the_index() {
		String plan = winningPlan("items", new Document("productId", "FI-SW-01"));

		assertThat(plan).contains("IXSCAN").contains("productId");
		assertThat(plan).doesNotContain("COLLSCAN");
	}

	@Test
	@DisplayName("GET_CATEGORY / GET_PRODUCT / GET_ITEM — the three findById reads ride the free _id index")
	void the_find_by_id_reads_use_the_id_index() {
		// The legacy spent a four-table locale-correlated join on GET_ITEM (CatalogDAOSQL.xml:92);
		// this is what it costs now. Asserted rather than assumed because "it is the primary key,
		// obviously" is exactly the kind of thing that stops being true when an @Id moves.
		//
		// The stage is IDHACK, not IXSCAN: an equality match on _id alone skips the query planner
		// altogether and goes straight to the index. That is a stronger result than IXSCAN, and it
		// is why this assertion cannot be written the same way as the two above — a rewrite that
		// turned these into ordinary index scans would still "use an index" and would still be a
		// regression worth seeing.
		assertThat(winningPlan("categories", new Document("_id", "FISH"))).isEqualTo("{\"stage\": \"IDHACK\"}");
		assertThat(winningPlan("products", new Document("_id", "FI-SW-01"))).isEqualTo("{\"stage\": \"IDHACK\"}");
		assertThat(winningPlan("items", new Document("_id", "EST-1"))).isEqualTo("{\"stage\": \"IDHACK\"}");
	}

	@Test
	@DisplayName("SEARCH_ITEMS (CatalogDAOSQL.xml:118) — search is a collection scan, and no index could change that")
	void search_is_a_collection_scan_and_that_is_not_a_regression() {
		// searchItems matches regexes against product.details.<locale>.name — a field that does not
		// exist until $lookup has synthesised it — so there is no collection for an index to be on.
		// Even the item-side branch could not be served: the criterion is an unanchored substring
		// (Pattern.quote of a keyword, no ^), and a B-tree index cannot answer "contains".
		//
		// The legacy was the same shape for the same reason: SEARCH_ITEMS was like '%keyword%',
		// which no relational index serves either. So this is a scan that was always a scan, and
		// the migration neither improved nor damaged it — which is the claim worth being able to
		// make, and the reason this test asserts COLLSCAN rather than quietly hoping for IXSCAN.
		String plan = winningPlan("items",
				new Document("details.en_US.description", new Document("$regex", "dog").append("$options", "i")));

		assertThat(plan).contains("COLLSCAN");
		assertThat(plan).doesNotContain("IXSCAN");
	}

	@Test
	@DisplayName("the $lookup half of search does use an index: it joins on _id")
	void the_lookup_side_of_search_is_indexed() {
		// $lookup localField productId -> foreignField _id, so the products side of the join is an
		// _id equality per input document. The scan is the items side and the regexes; the join
		// itself is not the expensive part, which is worth knowing before anyone "optimises" it.
		assertThat(winningPlan("products", new Document("_id", "FI-SW-01"))).doesNotContain("COLLSCAN");
	}

	private List<String> keysOf(String collection) {
		return this.mongo.getCollection(collection).listIndexes().into(new java.util.ArrayList<>()).stream()
				.map(index -> index.get("key", Document.class).toJson())
				.toList();
	}

	private String winningPlan(String collection, Document filter) {
		Document explain = this.mongo.getCollection(collection).find(filter).explain();
		return explain.get("queryPlanner", Document.class).get("winningPlan", Document.class).toJson();
	}

}
