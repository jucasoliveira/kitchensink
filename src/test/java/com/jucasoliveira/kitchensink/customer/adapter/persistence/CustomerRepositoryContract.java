package com.jucasoliveira.kitchensink.customer.adapter.persistence;

import java.util.List;

import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;
import com.jucasoliveira.kitchensink.customer.application.DuplicateAccountException;
import com.jucasoliveira.kitchensink.customer.domain.Account;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import com.jucasoliveira.kitchensink.customer.domain.CreditCard;
import com.jucasoliveira.kitchensink.customer.domain.Customer;
import com.jucasoliveira.kitchensink.customer.domain.PasswordHash;
import com.jucasoliveira.kitchensink.customer.domain.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Issue 4.6 — the four methods of {@link CustomerRepository}, through the port, against
 * <em>whichever</em> adapter the active profile resolves it to.
 *
 * <p>Legacy anchor: the CMP graph {@code CustomerEJB → AccountEJB → ContactInfoEJB → AddressEJB}
 * plus {@code CustomerEJB → ProfileEJB}, five entity beans in five container-generated tables
 * joined by CMR fields ({@code components/customer/src/ejb-jar.xml}, relationships section). Two
 * of those relations are {@code <cascade-delete/>}: the legacy already treated account, contact
 * info and profile as <em>owned</em> by the customer. That is an aggregate boundary CMP could only
 * spell as five tables and four joins.
 *
 * <p>This class holds the assertions and nothing else, deliberately mirroring
 * {@code CatalogRepositoryContract}. The subclasses supply a profile and a store:
 * {@code CustomerMongoRoundTripTest} boots a {@code mongo:7.0} container and adds the assertions
 * that can only be made against a raw {@code Document}; the JPA subclass that issue 4.6 adds boots
 * H2 under {@code --spring.profiles.active=jpa} and imports no Testcontainers configuration at
 * all. "Both profiles green" (AGENTS.md §5) is then one class run twice, not two classes that
 * happen to agree today and drift next week.
 *
 * <p>The test speaks to the port and never to an adapter, and it boots the real application
 * context rather than a {@code @DataMongoTest} / {@code @DataJpaTest} slice: what is being proven
 * is that the running application resolves the port to the right adapter under the profile it was
 * started with, which an {@code @Import} of the adapter would have assumed rather than shown.
 *
 * <p>Deliberately <em>not</em> {@code @Tag("parity")}. The legacy rules this slice preserves — the
 * duplicate-account refusal of {@code SignOnEJB.createUser}, the four {@code ProfileLocalHome}
 * defaults — are pinned as parity in {@code CustomerRegistrationTest} and {@code CustomerTest},
 * where they belong. What can break <em>here</em> is a mapper: a dropped field, a null that came
 * back as a blank, an {@code add()} that quietly became an upsert. Those are everyday correctness
 * concerns and should fail the everyday build, not only the parity job.
 *
 * <h2>The asymmetry this suite is really guarding</h2>
 *
 * <p>Under {@code mongo} the aggregate is one document and a null field is an absent key. Under
 * {@code jpa} it is five columns' worth of embeddables and a null field is a {@code NULL} column.
 * Those are not the same absence, and the port has to make them look the same — so every test
 * below that touches {@code streetName2} or {@code favoriteCategory} is there because that is
 * where the two stores are most likely to disagree while both stay green on their own.
 */
public abstract class CustomerRepositoryContract {

	/**
	 * Issue 1.8 folds the legacy {@code UserEJB} (userName, password — a separate entity joined to
	 * {@code CustomerEJB} on {@code userId = userName}) into the same aggregate as a BCrypt hash.
	 * Any well-formed hash will do: what is under test is that it round-trips, not that it matches
	 * anything.
	 */
	protected static final PasswordHash HASH = new PasswordHash(
			"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");

	protected static final PasswordHash OTHER_HASH = new PasswordHash(
			"$2a$10$Kx7bPqRs2mTuVwXyZa3bCd4eFg5hIj6kLm7nOp8qRs9tUv0wXy1zA");

	@Autowired
	protected CustomerRepository customers;

	/**
	 * Empties the store between tests. Abstract because there is no store-agnostic way to say it:
	 * the port has no {@code deleteAll} and should not grow one for the tests' convenience — the
	 * legacy {@code CustomerEJB} home declared only {@code findByPrimaryKey}, and the port has
	 * stayed close to that.
	 */
	protected abstract void clearStore();

	@BeforeEach
	void emptyTheStore() {
		clearStore();
	}

	@Test
	@DisplayName("the port is bound to the adapter of the active profile, and to nothing else")
	protected void the_port_resolves_to_the_adapter_of_this_profile() {
		// By package, not by class name: the rule LayeringRulesTest states is "the adapter lives in
		// the mongo package" / "in the jpa package", not "the adapter is called X". The subclass
		// sits in the package its adapter should be in, so getClass() is the expectation.
		// Unwrapped in case an exception-translation proxy sits in front of the bean.
		Class<?> adapter = AopProxyUtils.ultimateTargetClass(this.customers);
		assertThat(adapter.getPackageName()).isEqualTo(getClass().getPackageName());
	}

	@Test
	@DisplayName("a registered customer reads back equal, every nested CMP field included")
	protected void a_registered_customer_round_trips() {
		Customer registered = Customer.register("ada", HASH, contact());

		this.customers.add(registered);
		Customer loaded = this.customers.findByUserId("ada").orElseThrow();

		// Record equality walks the whole graph in one assertion — status, contact info, address,
		// profile: every CMP field of all five beans. A mapper that dropped one fails here.
		assertThat(loaded).isEqualTo(registered);
	}

	@Test
	@DisplayName("add() returns the stored aggregate, not the argument handed to it")
	protected void add_returns_what_was_stored() {
		// Worth stating because the two adapters get here differently: Mongo maps the inserted
		// document back, JPA returns a managed entity mapped back. A caller that trusted the
		// return value would be trusting two different objects, so they have to agree.
		Customer registered = this.customers.add(Customer.register("ada", HASH, contact()));

		assertThat(registered).isEqualTo(this.customers.findByUserId("ada").orElseThrow());
	}

	@Test
	@DisplayName("an unknown userId is Optional.empty(), not an exception and not a null")
	protected void an_unknown_user_id_is_empty() {
		// The CMP equivalent was a thrown ObjectNotFoundException out of findByPrimaryKey, which
		// every caller had to catch. The port makes the miss a value, the same choice
		// CatalogRepositoryContract pins for the catalog's "return null" finders.
		assertThat(this.customers.findByUserId("nobody")).isEmpty();
	}

	@Test
	@DisplayName("findAll returns every registered customer, whole, and an empty list on an empty store")
	protected void find_all_returns_every_customer() {
		// The legacy had no finder for this: CustomerEJB's home declares only findByPrimaryKey and
		// customer.screen showed one account. The list is the kitchensink twin's table, and the
		// port grew the one method that screen needs — no more.
		assertThat(this.customers.findAll()).isEmpty();

		Customer ada = this.customers.add(Customer.register("ada", HASH, contact()));
		Customer grace = this.customers.add(Customer.register("grace", HASH, contact()));

		assertThat(this.customers.findAll()).containsExactlyInAnyOrder(ada, grace);
	}

	@Test
	@DisplayName("Issue 4.4 — the store is the second gate: a duplicate userId is refused, not upserted")
	protected void a_duplicate_user_id_is_refused_by_the_store() {
		// CustomerRegistration checks findByUserId first, so in the running application this path
		// is only reached when two registrations race between that check and this write. That is
		// why the test speaks to the port directly: going through the service would prove the
		// service's guard again and never touch the guard being tested here.
		//
		// The legacy had only this half. SignOnEJB.createUser was one line, ulh.create(userName,
		// password), and the CMP container refused the second create on the same primary key. Both
		// adapters have that same mechanism available — a unique _id, a primary key — and the
		// adapter's job is only to name the failure.
		Customer ada = this.customers.add(Customer.register("ada", HASH, contact()));

		ContactInfo impostor = new ContactInfo("Not", "Ada", "029 2018 0000", "impostor@example.com",
				new Address("2 Other St", null, "Cardiff", "CDF", "CF10 1AA", "GB"));

		assertThatExceptionOfType(DuplicateAccountException.class)
			.isThrownBy(() -> this.customers.add(Customer.register("ada", OTHER_HASH, impostor)))
			.satisfies(taken -> assertThat(taken.userId()).isEqualTo("ada"));

		// The failure mode this replaces was silent: a save() on an aggregate whose id is already
		// set is an upsert, so the second registration used to overwrite the first — same row
		// count, different owner, different password. Counting is not enough to catch that, so the
		// survivor is compared whole.
		assertThat(this.customers.findByUserId("ada")).contains(ada);
		assertThat(this.customers.findAll()).containsExactly(ada);
	}

	@Test
	@DisplayName("ProfileLocalHome.java:44-47 — the four profile defaults survive the mapping, null favoriteCategory included")
	protected void the_default_profile_round_trips() {
		this.customers.add(Customer.register("ada", HASH, contact()));

		Profile profile = this.customers.findByUserId("ada").orElseThrow().profile();

		assertThat(profile).isEqualTo(Profile.DEFAULT);
		assertThat(profile.preferredLanguage()).isEqualTo("en_US");
		assertThat(profile.myListPreference()).isTrue();
		assertThat(profile.bannerPreference()).isTrue();
		// An absent document key under mongo, a NULL column under jpa. Both must arrive as null,
		// and neither may helpfully substitute "" — that would make the profile screen render a
		// blank selected category where the legacy rendered none.
		assertThat(profile.favoriteCategory()).isNull();
	}

	@Test
	@DisplayName("ProfileEJB.java:52-62 — a non-default profile round-trips whole, the two unbuilt flags included")
	protected void a_non_default_profile_round_trips() {
		// ProfileEJB has four CMP fields and the migration carries all four, so all four have to
		// survive. The two booleans gate T3 features nothing reads yet, which is precisely when a
		// dropped field goes unnoticed — and a boolean that silently defaults to false rather than
		// failing is the quietest bug in the set.
		Customer stored = new Customer("ada", HASH, Customer.register("ada", HASH, contact()).account(),
				new Profile("ja_JP", "FISH", false, false));

		this.customers.add(stored);

		assertThat(this.customers.findByUserId("ada")).contains(stored);
	}

	@Test
	@DisplayName("AddressEJB.streetName2 — an absent optional field reads back null, not an empty string")
	protected void an_absent_optional_field_stays_absent() {
		// Same asymmetry as favoriteCategory, one bean lower, and stated separately because the
		// two stores reach it by different routes: Spring Data omits the key on write and has to
		// restore it on read, while JPA writes a NULL column. A round trip that turned either into
		// "" would pass every equality check that used isEqualToIgnoringNullFields.
		this.customers.add(Customer.register("ada", HASH, contact()));

		Address address = this.customers.findByUserId("ada").orElseThrow().account().contactInfo().address();

		assertThat(address.streetName2()).isNull();
		assertThat(address.streetName1()).isEqualTo("1 Main St");
		assertThat(address.country()).isEqualTo("GB");
	}

	@Test
	@DisplayName("Issue 4.5 — update() replaces the stored customer rather than adding a second")
	protected void an_update_replaces_the_stored_customer() {
		// The port grew update() for the profile screen (CustomerEJBAction.java:138-141, which
		// mutated the CMP beans in place). add() must stay insert-only — that is the #25 rule — so
		// the two methods deliberately do not share an implementation, and this test is what stops
		// a later "simplification" from collapsing them back into one save().
		Customer registered = this.customers.add(Customer.register("ada", HASH, contact()));
		Customer switched = new Customer(registered.userId(), registered.passwordHash(), registered.account(),
				new Profile("ja_JP", "FISH", true, true));

		Customer updated = this.customers.update(switched);

		assertThat(updated).isEqualTo(switched);
		assertThat(this.customers.findByUserId("ada")).contains(switched);
		assertThat(this.customers.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("update() does not reopen the credential: the stored hash is whatever was written at registration")
	protected void an_update_leaves_the_password_hash_alone() {
		// The credential is not the profile screen's to change, and an update that rewrote the
		// whole aggregate from a form would be the #25 account-takeover again by another route.
		// The port cannot prevent a caller passing a different hash — CustomerRegistration
		// .updateProfile is what carries the old one forward — so what is pinned here is that the
		// adapter does not lose it, which is the half the adapter owns.
		Customer registered = this.customers.add(Customer.register("ada", HASH, contact()));

		this.customers.update(new Customer(registered.userId(), registered.passwordHash(), registered.account(),
				new Profile("ja_JP", null, true, true)));

		assertThat(this.customers.findByUserId("ada").orElseThrow().passwordHash()).isEqualTo(HASH);
	}

	@Test
	@DisplayName("characterization: update() on an unregistered userId writes it rather than failing")
	protected void an_update_of_an_unknown_customer_inserts() {
		// Not a rule anyone chose — it is what save() does under both stores, and it is written
		// down so the two adapters cannot disagree about it silently. The legacy could not reach
		// this state at all: CustomerEJBAction only ever mutated beans it had already loaded.
		//
		// If update() should instead refuse an unknown userId, this is the single test to invert,
		// and both adapters then need an existence check. Left as-is because no caller reaches it:
		// CustomerRegistration.updateProfile loads first and throws on a miss.
		Customer never = new Customer("ghost", HASH, Customer.register("ghost", HASH, contact()).account(),
				Profile.DEFAULT);

		this.customers.update(never);

		assertThat(this.customers.findByUserId("ghost")).contains(never);
	}

	@Test
	@DisplayName("two customers are two aggregates: writing one does not disturb the other")
	protected void aggregates_do_not_bleed_into_each_other() {
		// Cheap under mongo, where they are separate documents. The reason it is in the contract
		// is jpa: the embeddables of issue 4.1 land in shared tables, and a mapping that keyed an
		// address row on something other than the owning customer would pass every single-customer
		// test above and fail here.
		Customer ada = this.customers.add(Customer.register("ada", HASH, contact()));
		Customer grace = this.customers.add(Customer.register("grace", OTHER_HASH,
				new ContactInfo("Grace", "Hopper", "+1 212 555 0100", "grace@example.com",
						new Address("3 Navy Yard", "Apt 4", "New York", "NY", "10001", "US"))));

		this.customers.update(new Customer(ada.userId(), ada.passwordHash(), ada.account(),
				new Profile("ja_JP", "FISH", false, false)));

		assertThat(this.customers.findByUserId("grace")).contains(grace);
		assertThat(this.customers.findByUserId("grace").orElseThrow().account().contactInfo().address().streetName2())
			.isEqualTo("Apt 4");
	}

	@Test
	@DisplayName("Issue 4.1 / AccountEJB.java:87-89 — registration stores the empty card the legacy created, not a null")
	protected void the_empty_credit_card_survives_registration() {
		this.customers.add(Customer.register("ada", HASH, contact()));

		CreditCard card = this.customers.findByUserId("ada").orElseThrow().account().creditCard();

		assertThat(card).isEqualTo(CreditCard.EMPTY);
		assertThat(card.cardNumber()).isNull();
		assertThat(card.cardType()).isNull();
		assertThat(card.expiryDate()).isNull();
	}

	@Test
	@DisplayName("Issue 4.1 — CreditCardEJB's three CMP fields round-trip when they are actually populated")
	protected void a_populated_credit_card_round_trips() {
		// The test above cannot catch an adapter that drops the card entirely: every component of
		// CreditCard.EMPTY is null, so a mapper that never wrote the field and rebuilt EMPTY on
		// read would pass it and pass the whole-record equality check as well. This one puts a
		// distinct value in each of the three columns.
		//
		// Nothing in the delivered slice reaches this state. AccountEJB.ejbPostCreate created the
		// card empty, and the screens that filled it — enter_order_information.screen and the
		// checkout flow — are T3, deferred under ADR-0006. The field is carried for structural
		// parity with finding #4, so what is proven here is that it *would* carry data, not that
		// anything in T1/T2 puts data in it. The card number is an obvious placeholder for the
		// same reason: no real PAN is ever collected by this application.
		Customer registered = Customer.register("ada", HASH, contact());
		Customer withCard = new Customer(registered.userId(), registered.passwordHash(),
				new Account(registered.account().status(), registered.account().contactInfo(),
						new CreditCard("0000-0000-0000-0000", "Visa", "12/2003")),
				registered.profile());

		this.customers.add(withCard);

		assertThat(this.customers.findByUserId("ada")).contains(withCard);
	}

	/**
	 * One value per {@code ContactInfoEJB} / {@code AddressEJB} CMP field
	 * ({@code components/customer/src/ejb-jar.xml}). {@code streetName2} is left null on purpose —
	 * it is the only optional field in the graph and several tests above depend on that.
	 *
	 * <p>The credit card is deliberately not here: {@code AccountEJB 1—1 CreditCardEJB} hangs off
	 * the account, not off contact info, and {@code Customer.register} attaches
	 * {@link CreditCard#EMPTY} itself — see {@link #the_empty_credit_card_survives_registration()}.
	 */
	protected static ContactInfo contact() {
		return new ContactInfo("Ada", "Lovelace", "020 7946 0000", "ada@example.com",
				new Address("1 Main St", null, "London", "LDN", "N1 1AA", "GB"));
	}

}
