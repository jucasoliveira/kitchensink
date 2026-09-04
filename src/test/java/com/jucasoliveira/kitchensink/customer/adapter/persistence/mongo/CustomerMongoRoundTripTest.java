package com.jucasoliveira.kitchensink.customer.adapter.persistence.mongo;

import java.util.ArrayList;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;
import com.jucasoliveira.kitchensink.customer.application.DuplicateAccountException;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import com.jucasoliveira.kitchensink.customer.domain.Customer;
import com.jucasoliveira.kitchensink.customer.domain.PasswordHash;
import com.jucasoliveira.kitchensink.customer.domain.Profile;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Issue 1.7 — the walking skeleton's Mongo round-trip.
 *
 * <p>Legacy anchor: the CMP graph {@code CustomerEJB → AccountEJB → ContactInfoEJB → AddressEJB},
 * four entity beans in four container-generated tables (finding #2) joined by CMR fields
 * ({@code customer/src/ejb-jar.xml:265-306}). Two of those relations are
 * {@code <cascade-delete/>} ({@code ejb-jar.xml:280}, {@code :343}): the legacy already treated
 * account and contact info as <em>owned</em> by the customer. That is an aggregate boundary the
 * CMP model could only spell as four tables and three joins; here it is one document.
 *
 * <p>The test speaks to the <em>port</em>, {@link CustomerRepository}, never to the adapter, and
 * it boots the real application context rather than a {@code @DataMongoTest} slice. What is being
 * proven is that the running application resolves the port to the Mongo adapter under the default
 * profile — something an {@code @Import} of the adapter would have assumed rather than shown.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CustomerMongoRoundTripTest {

	/** ADR-0005 "Document design": one {@code customers} document per account. */
	static final String COLLECTION = "customers";

	/**
	 * Issue 1.8 folds the legacy {@code UserEJB} (userName, password — a separate entity joined to
	 * {@code CustomerEJB} on {@code userId = userName}, {@code 01-legacy-architecture.md} §4) into
	 * the same document as a BCrypt hash. Any well-formed hash will do here; what is under test is
	 * that it round-trips, not that it matches anything.
	 */
	static final PasswordHash HASH = new PasswordHash("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");

	@Autowired
	CustomerRepository customers;

	@Autowired
	MongoTemplate template;

	@BeforeEach
	void reset() {
		this.template.dropCollection(COLLECTION);
	}

	@Test
	@DisplayName("under the default profile the port is bound to the Mongo adapter")
	void the_port_resolves_to_the_mongo_adapter() {
		// By package, not by class name: the rule is "the adapter lives in the mongo package"
		// (LayeringRulesTest), not "the adapter is called X". Unwrapped in case @Repository's
		// exception-translation proxy is in front of it.
		Class<?> adapter = AopProxyUtils.ultimateTargetClass(this.customers);
		assertThat(adapter.getPackageName()).isEqualTo(getClass().getPackageName());
	}

	@Test
	@DisplayName("a registered customer reads back equal, nested values included")
	void a_registered_customer_round_trips() {
		Customer registered = Customer.register("ada", HASH, contact());

		this.customers.add(registered);
		Customer loaded = this.customers.findByUserId("ada").orElseThrow();

		// Record equality walks the whole graph — status, contact info, address: every CMP field
		// of the four beans — including the null streetName2, which Spring Data omits on write
		// and has to restore on read.
		assertThat(loaded).isEqualTo(registered);
	}

	@Test
	@DisplayName("an unknown userId is empty, not an exception")
	void an_unknown_user_id_is_empty() {
		assertThat(this.customers.findByUserId("nobody")).isEmpty();
	}

	@Test
	@DisplayName("Issue 1.9 — findAll returns every registered customer, whole, and nothing on an empty store")
	void find_all_returns_every_customer() {
		// The legacy had no finder for this: CustomerEJB's home declares only findByPrimaryKey
		// (customer/src/ejb-jar.xml) and customer.screen showed one account. The list is the
		// kitchensink twin's table, and the port grows the one method the screen needs.
		assertThat(this.customers.findAll()).isEmpty();

		Customer ada = this.customers.add(Customer.register("ada", HASH, contact()));
		Customer grace = this.customers.add(Customer.register("grace", HASH, contact()));

		assertThat(this.customers.findAll()).containsExactlyInAnyOrder(ada, grace);
	}

	@Test
	@DisplayName("Issue 4.4 — the store is the second gate: a duplicate user id is refused, not upserted")
	void a_duplicate_user_id_is_refused_by_the_store() {
		// CustomerRegistration checks findByUserId first, so in the running application this path
		// is only reached when two registrations race between that check and this write. That is
		// exactly why the test speaks to the port directly: going through the service would prove
		// the service's guard again and never touch the guard being tested here.
		//
		// The legacy had only this half. SignOnEJB.createUser (SignOnEJB.java:80-82) was one line,
		// ulh.create(userName, password), and the CMP container refused the second create on the
		// same primary key. The document _id is that primary key (see the test below), so the
		// refusal is the same mechanism, and the adapter's job is only to name it.
		//
		// Deliberately NOT @Tag("parity"), unlike its twin in CustomerRegistrationTest: what can
		// break here is the adapter's exception translation, which is a plain correctness concern
		// and belongs in the everyday build rather than only in the parity job.
		Customer ada = this.customers.add(Customer.register("ada", HASH, contact()));

		PasswordHash otherHash = new PasswordHash("$2a$10$Kx7bPqRs2mTuVwXyZa3bCd4eFg5hIj6kLm7nOp8qRs9tUv0wXy1zA");
		ContactInfo impostor = new ContactInfo("Not", "Ada", "029 2018 0000", "impostor@example.com",
				new Address("2 Other St", null, "Cardiff", "CDF", "CF10 1AA", "GB"));

		assertThatExceptionOfType(DuplicateAccountException.class)
			.isThrownBy(() -> this.customers.add(Customer.register("ada", otherHash, impostor)))
			.satisfies(taken -> assertThat(taken.userId()).isEqualTo("ada"));

		// The failure mode this replaces was silent: MongoRepository.save() on a record whose @Id
		// is already set is an upsert, so the second registration used to overwrite the first —
		// same document count, different owner, different password. Counting is not enough to
		// catch that, so the surviving document is compared whole.
		assertThat(this.template.getCollection(COLLECTION).countDocuments()).isEqualTo(1);
		assertThat(this.customers.findByUserId("ada")).contains(ada);
		assertThat(this.customers.findAll()).containsExactly(ada);
	}

	@Test
	@DisplayName("the four-bean CMP graph is stored as ONE document in ONE collection")
	void the_cmp_graph_is_one_document() {
		this.customers.add(Customer.register("ada", HASH, contact()));

		Document raw = this.template.getCollection(COLLECTION).find().first();
		assertThat(raw).isNotNull();

		// The CustomerEJB primary key (ejb-jar.xml:59, <field-name>userId</field-name>) is the
		// document identity, not a field sitting beside a generated ObjectId.
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

		// And there is no second, third or fourth collection to join against. The database is
		// fresh per container and nothing else in this context writes, so "exactly" is safe.
		assertThat(this.template.getDb().listCollectionNames().into(new ArrayList<>())).containsExactly(COLLECTION);
	}

	@Test
	@DisplayName("Issue 4.5 — ProfileEJB is a fifth bean in the same document, a sibling of account")
	void the_profile_is_stored_beside_the_account() {
		// CustomerEJB.ejbPostCreate:79-84 creates AccountLocal and ProfileLocal side by side and
		// setAccount/setProfile them onto the customer — two CMR fields, not one nested in the
		// other (customer/src/ejb-jar.xml). The document mirrors that: profile is a top-level
		// subdocument, and putting it under account would be a quiet change of the model.
		this.customers.add(Customer.register("ada", HASH, contact()));

		Document raw = this.template.getCollection(COLLECTION).find().first();
		assertThat(raw).isNotNull();
		Document profile = raw.get("profile", Document.class);
		assertThat(profile).isNotNull();
		assertThat(raw.get("account", Document.class)).doesNotContainKey("profile");

		// ProfileLocalHome.java:44-47, all four defaults. favoriteCategory is null there, and
		// Spring Data omits a null on write exactly as it does for streetName2 above.
		assertThat(profile).containsEntry("preferredLanguage", "en_US")
			.containsEntry("myListPreference", true)
			.containsEntry("bannerPreference", true)
			.doesNotContainKey("favoriteCategory");
	}

	@Test
	@DisplayName("Issue 4.5 — a profile round-trips whole, including the two unbuilt preference flags")
	void a_profile_round_trips() {
		// ProfileEJB.java:52-62 has four CMP fields and the migration carries all four, so all four
		// have to survive the mapping. The two booleans gate T3 features nothing reads yet, which
		// is precisely when a dropped field goes unnoticed.
		Customer stored = new Customer("ada", HASH, Customer.register("ada", HASH, contact()).account(),
				new Profile("ja_JP", "FISH", false, false));

		this.customers.add(stored);

		assertThat(this.customers.findByUserId("ada")).contains(stored);
	}

	@Test
	@DisplayName("Issue 4.5 — update() replaces the stored customer rather than adding a second")
	void an_update_replaces_the_stored_customer() {
		// The port grew update() for the profile screen (CustomerEJBAction.java:138-141, which
		// mutated the CMP beans in place). add() must stay insert-only — that is the #25 rule — so
		// the two methods deliberately do not share an implementation.
		Customer registered = this.customers.add(Customer.register("ada", HASH, contact()));
		Customer switched = new Customer(registered.userId(), registered.passwordHash(), registered.account(),
				new Profile("ja_JP", "FISH", true, true));

		Customer updated = this.customers.update(switched);

		assertThat(updated).isEqualTo(switched);
		assertThat(this.customers.findByUserId("ada")).contains(switched);
		assertThat(this.template.getCollection(COLLECTION).countDocuments()).isEqualTo(1);
		// The credential is not the profile screen's to change, and an update that rewrote the
		// whole document from a form would be the #25 takeover again by another route.
		assertThat(this.customers.findByUserId("ada").orElseThrow().passwordHash()).isEqualTo(HASH);
	}

	/** One value per ContactInfoEJB / AddressEJB CMP field ({@code customer/src/ejb-jar.xml:161-223}). */
	static ContactInfo contact() {
		return new ContactInfo("Ada", "Lovelace", "020 7946 0000", "ada@example.com",
				new Address("1 Main St", null, "London", "LDN", "N1 1AA", "GB"));
	}

}
