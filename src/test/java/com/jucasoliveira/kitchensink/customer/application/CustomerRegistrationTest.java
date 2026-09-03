package com.jucasoliveira.kitchensink.customer.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.jucasoliveira.kitchensink.customer.domain.AccountStatus;
import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import com.jucasoliveira.kitchensink.customer.domain.Customer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issue 1.8 — registration stores a hash, never the password.
 *
 * <p>Legacy: {@code SignOnEJB.createUser} ({@code signon/.../ejb/SignOnEJB.java:80-82}) handed the
 * password straight to {@code UserEJB.ejbCreate}, which stored it as typed
 * ({@code UserEJB.java:79}, {@code setPassword(password)}). The replacement is one application
 * service that hashes on the way in, so that the only thing the repository port ever sees is a
 * {@code PasswordHash} — and the port's signature makes that the only thing it <em>can</em> see.
 *
 * <p>The repository is an in-memory fake against the port, not a Mongo container: this is a test
 * of the application layer's rule, and the store is not part of the rule. The encoder is the real
 * BCrypt one at its cheapest cost factor, because a fake encoder would prove nothing about the
 * property under test.
 *
 * <p>Issue 2.2: {@code @Tag("parity")} sits on two methods, not the class - the other two are this
 * service's main coverage in the {@code build} job's JaCoCo bundle, and tagging the class would
 * exclude them there ({@code -DexcludedGroups=parity}) with nothing else in {@code application}
 * to make up the branches. {@link #equal_passwords_are_not_equal_at_rest()} pins the hashing
 * deviation, and {@link #a_duplicate_user_id_is_rejected_not_silently_overwritten()} pins the
 * duplicate-account rule. That one is {@code @Disabled} rather than left red:
 * {@link CustomerRegistration#register} does not check for an existing user id yet, so it fails
 * until Issue 4.4 (#25) adds the rejection — re-enable it then, so it stops being a pinned
 * intention and starts being the parity gate.
 */
class CustomerRegistrationTest {

	static final Address ADDRESS = new Address("1 Main St", null, "London", "LDN", "N1 1AA", "GB");

	static final ContactInfo CONTACT = new ContactInfo("Ada", "Lovelace", "020 7946 0000", "ada@example.com",
			ADDRESS);

	/** Cost 4 is the minimum BCrypt allows; the production bean uses the default of 10. */
	final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

	final InMemoryCustomers customers = new InMemoryCustomers();

	final CustomerRegistration registration = new CustomerRegistration(this.customers, this.encoder);

	@Test
	@DisplayName("what is stored is a BCrypt hash that matches the password, and is not the password")
	void the_stored_credential_is_a_hash_of_the_password() {
		this.registration.register(new RegisterCustomerCommand("ada", "s3cret", CONTACT));

		String stored = this.customers.findByUserId("ada").orElseThrow().passwordHash().value();
		assertThat(stored).isNotEqualTo("s3cret").doesNotContain("s3cret").startsWith("$2a$");
		assertThat(this.encoder.matches("s3cret", stored)).isTrue();
		assertThat(this.encoder.matches("S3cret", stored)).isFalse();
	}

	@Test
	@Tag("parity")
	@DisplayName("two customers with the same password do not share a hash")
	void equal_passwords_are_not_equal_at_rest() {
		// Under UserEJB.java:88 every "j2ee" in the seed (Populate-UTF8.xml:74,77,80,83) was the
		// same four bytes in the same column. A salted hash makes them unrelated strings.
		this.registration.register(new RegisterCustomerCommand("j2ee", "j2ee", CONTACT));
		this.registration.register(new RegisterCustomerCommand("shopper", "j2ee", CONTACT));

		assertThat(this.customers.findByUserId("j2ee").orElseThrow().passwordHash())
			.isNotEqualTo(this.customers.findByUserId("shopper").orElseThrow().passwordHash());
	}

	@Test
	@DisplayName("registration goes through the aggregate's own rule: the account is created active")
	void registration_creates_an_active_account() {
		// CustomerEJB.java:78 — the 1.7 rule, reached through the service rather than around it.
		Customer registered = this.registration.register(new RegisterCustomerCommand("ada", "s3cret", CONTACT));

		assertThat(registered.userId()).isEqualTo("ada");
		assertThat(registered.account().status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(registered.account().contactInfo()).isEqualTo(CONTACT);
		assertThat(registered).isEqualTo(this.customers.findByUserId("ada").orElseThrow());
	}

	@Test
	@Tag("parity")
	@Disabled("pinned ahead of Issue 4.4 (#25) - CustomerRegistration.register() does not reject "
			+ "a duplicate user id yet, so this fails until that lands. Re-enable then.")
	@DisplayName("CreateUserEJBAction.java:89-100 — a second registration with an existing user id is rejected, not silently overwritten")
	void a_duplicate_user_id_is_rejected_not_silently_overwritten() {
		// Legacy: SignOnEJB.createUser is a CMP entity create keyed on userName
		// (signon/.../ejb/SignOnEJB.java:80-82); a second create with the same primary key throws
		// javax.ejb.DuplicateKeyException, which CreateUserEJBAction.java:99-100 catches and
		// rethrows as DuplicateAccountException("Bad UserName or password") — routed to
		// duplicate_account.screen rather than replacing the first account.
		this.registration.register(new RegisterCustomerCommand("ada", "s3cret", CONTACT));

		ContactInfo impostor = new ContactInfo("Not", "Ada", "029 2018 0000", "impostor@example.com", ADDRESS);
		assertThatThrownBy(
				() -> this.registration.register(new RegisterCustomerCommand("ada", "different-password", impostor)))
			.isInstanceOf(RuntimeException.class);

		// The original account survives untouched: still Ada's contact info, still the first password.
		Customer stillAda = this.customers.findByUserId("ada").orElseThrow();
		assertThat(stillAda.account().contactInfo()).isEqualTo(CONTACT);
		assertThat(this.encoder.matches("s3cret", stillAda.passwordHash().value())).isTrue();
		assertThat(this.customers.findAll()).hasSize(1);
	}

	/** The port, in a map. Enough to prove the rule; the adapters have their own tests. */
	static final class InMemoryCustomers implements CustomerRepository {

		private final Map<String, Customer> byUserId = new HashMap<>();

		@Override
		public Customer save(Customer customer) {
			this.byUserId.put(customer.userId(), customer);
			return customer;
		}

		@Override
		public Optional<Customer> findByUserId(String userId) {
			return Optional.ofNullable(this.byUserId.get(userId));
		}

		@Override
		public List<Customer> findAll() {
			return List.copyOf(this.byUserId.values());
		}

	}

}
