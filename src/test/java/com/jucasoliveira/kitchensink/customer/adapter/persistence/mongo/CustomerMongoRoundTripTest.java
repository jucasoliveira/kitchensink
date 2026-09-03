package com.jucasoliveira.kitchensink.customer.adapter.persistence.mongo;

import java.util.ArrayList;

import com.jucasoliveira.kitchensink.TestcontainersConfiguration;
import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import com.jucasoliveira.kitchensink.customer.domain.Customer;
import com.jucasoliveira.kitchensink.customer.domain.PasswordHash;
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

		this.customers.save(registered);
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

		Customer ada = this.customers.save(Customer.register("ada", HASH, contact()));
		Customer grace = this.customers.save(Customer.register("grace", HASH, contact()));

		assertThat(this.customers.findAll()).containsExactlyInAnyOrder(ada, grace);
	}

	@Test
	@DisplayName("the four-bean CMP graph is stored as ONE document in ONE collection")
	void the_cmp_graph_is_one_document() {
		this.customers.save(Customer.register("ada", HASH, contact()));

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

	/** One value per ContactInfoEJB / AddressEJB CMP field ({@code customer/src/ejb-jar.xml:161-223}). */
	static ContactInfo contact() {
		return new ContactInfo("Ada", "Lovelace", "020 7946 0000", "ada@example.com",
				new Address("1 Main St", null, "London", "LDN", "N1 1AA", "GB"));
	}

}
