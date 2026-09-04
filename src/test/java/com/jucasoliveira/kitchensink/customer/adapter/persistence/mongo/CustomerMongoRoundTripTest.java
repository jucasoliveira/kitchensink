package com.jucasoliveira.kitchensink.customer.adapter.persistence.mongo;

import java.util.ArrayList;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import com.jucasoliveira.kitchensink.customer.adapter.persistence.CustomerRepositoryContract;
import com.jucasoliveira.kitchensink.customer.domain.Customer;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 1.7 — the walking skeleton's Mongo round-trip, now {@link CustomerRepositoryContract}
 * against the Mongo adapter under the default profile.
 *
 * <p>Every store-agnostic assertion — round-trip equality, the duplicate refusal, the profile
 * defaults, {@code update()} replacing rather than appending — lives in the contract, so that the
 * JPA adapter of issue 4.6 inherits exactly the same expectations rather than a copy of them that
 * agrees today and drifts next week. It activates no profile on purpose: ADR-0005 §4 makes
 * {@code mongo} the default, and the way to test a default is to not name it (same reasoning as
 * {@code PersistenceProfileMongoTest}).
 *
 * <p>What stays here is everything that can only be said against a raw {@code Document}: the
 * shape. The contract can prove the aggregate survives a round trip; it cannot prove it survives
 * it as <em>one document in one collection</em>, and that claim is the entire point of finding #2
 * and of ADR-0005's document design. A mapper that split the graph back into five collections
 * would pass every test in the contract.
 *
 * <p>Legacy anchor: the CMP graph {@code CustomerEJB → AccountEJB → ContactInfoEJB → AddressEJB},
 * with {@code AccountEJB 1—1 CreditCardEJB} alongside (issue 4.1) — five entity beans in five
 * container-generated tables (finding #2) joined by CMR fields
 * ({@code customer/src/ejb-jar.xml:265-306}). Two of those relations are {@code <cascade-delete/>}
 * ({@code ejb-jar.xml:280}, {@code :343}): the legacy already treated account and contact info as
 * <em>owned</em> by the customer. That is an aggregate boundary the CMP model could only spell as
 * four tables and three joins; here it is one document, and the tests below are what says so.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CustomerMongoRoundTripTest extends CustomerRepositoryContract {

	/** ADR-0005 "Document design": one {@code customers} document per account. */
	static final String COLLECTION = "customers";

	@Autowired
	MongoTemplate template;

	@Override
	protected void clearStore() {
		this.template.dropCollection(COLLECTION);
	}

	@Test
	@DisplayName("the four-bean CMP graph is stored as ONE document in ONE collection")
	void the_cmp_graph_is_one_document() {
		this.customers.add(Customer.register("ada", HASH, contact()));

		Document raw = this.template.getCollection(COLLECTION).find().first();
		assertThat(raw).isNotNull();

		// The CustomerEJB primary key (ejb-jar.xml:59, <field-name>userId</field-name>) is the
		// document identity, not a field sitting beside a generated ObjectId. This is also what
		// makes the contract's duplicate test work at all: the refusal is a unique _id, the same
		// mechanism the CMP container used, and the adapter only names it.
		assertThat(raw.getString("_id")).isEqualTo("ada");
		assertThat(raw).doesNotContainKey("userId");

		// The UserEJB half of the "userId = userName" join is a field of the same document, stored
		// as the bare hash string rather than a wrapper subdocument (Issue 1.8, finding #1).
		assertThat(raw.getString("passwordHash")).isEqualTo(HASH.value());
		assertThat(raw).doesNotContainKey("password");

		// AccountEJB / ContactInfoEJB / AddressEJB are subdocuments nested the way the CMR fields
		// nest: customer.account.contactInfo.address (ejb-jar.xml:274, :337, :295).
		Document account = raw.get("account", Document.class);
		assertThat(account).containsEntry("status", "ACTIVE");
		Document contactInfo = account.get("contactInfo", Document.class);
		assertThat(contactInfo).containsEntry("givenName", "Ada")
			.containsEntry("familyName", "Lovelace")
			.containsEntry("telephone", "020 7946 0000")
			.containsEntry("email", "ada@example.com");
		Document address = contactInfo.get("address", Document.class);
		assertThat(address).containsEntry("streetName1", "1 Main St")
			.doesNotContainKey("streetName2")
			.containsEntry("city", "London")
			.containsEntry("state", "LDN")
			.containsEntry("zipCode", "N1 1AA")
			.containsEntry("country", "GB");

		// AccountEJB.ejbPostCreate:87-89 created an EMPTY CreditCardLocal beside the contact info,
		// so the faithful document has the card PRESENT and empty: a row that exists with three
		// null columns, which Spring Data writes as {} because it omits every null component. An
		// absent key would mean "no card row at all", which is not what the legacy did — and is
		// exactly what a mapper that treated CreditCard.EMPTY as a null would produce.
		Document card = account.get("creditCard", Document.class);
		assertThat(card).isNotNull().isEmpty();

		// And there is no second, third, fourth or fifth collection to join against — named one by
		// one, after the CMP entity beans whose container-generated tables they would have been.
		//
		// This used to assert the database held EXACTLY one collection, which was true only while
		// the customer tests ran alone. The catalog suites share the Testcontainers instance and
		// seed products and items into the same database, so the assertion passed on ordering
		// rather than on anything it meant to say — issue 7.4's profile-switch script, which runs
		// the customer and catalog contracts in one JVM, is what surfaced it. Naming the
		// collections that must NOT exist is both the claim actually being made and independent of
		// what else is in the database.
		assertThat(this.template.getDb().listCollectionNames().into(new ArrayList<>()))
			.contains(COLLECTION)
			.doesNotContain("accounts", "contactinfo", "addresses", "creditcards", "profiles");
	}

	@Test
	@DisplayName("Issue 4.5 — ProfileEJB is a fifth bean in the same document, a sibling of account")
	void the_profile_is_stored_beside_the_account() {
		// CustomerEJB.ejbPostCreate:79-84 creates AccountLocal and ProfileLocal side by side and
		// setAccount/setProfile them onto the customer — two CMR fields, not one nested in the
		// other (customer/src/ejb-jar.xml). The document mirrors that: profile is a top-level
		// subdocument, and putting it under account would be a quiet change of the model that no
		// round-trip assertion in the contract would notice.
		this.customers.add(Customer.register("ada", HASH, contact()));

		Document raw = this.template.getCollection(COLLECTION).find().first();
		assertThat(raw).isNotNull();
		Document profile = raw.get("profile", Document.class);
		assertThat(profile).isNotNull();
		assertThat(raw.get("account", Document.class)).doesNotContainKey("profile");

		// ProfileLocalHome.java:44-47, all four defaults. favoriteCategory is null there, and
		// Spring Data omits a null on write exactly as it does for streetName2 above — the
		// contract asserts it arrives back as null, this asserts it was never written.
		assertThat(profile).containsEntry("preferredLanguage", "en_US")
			.containsEntry("myListPreference", true)
			.containsEntry("bannerPreference", true)
			.doesNotContainKey("favoriteCategory");
	}

}
