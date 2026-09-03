package com.jucasoliveira.kitchensink.customer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 1.7 — the one registration rule the walking skeleton carries.
 *
 * <p>Legacy: {@code CustomerEJB.ejbPostCreate} creates the account itself, with
 * {@code AccountLocalHome.Active} ({@code customer/.../ejb/CustomerEJB.java:78},
 * {@code account/ejb/AccountLocalHome.java:48}). A customer never exists without an account, and
 * a new account is always active. Pure Java, no Spring and no container — ADR-0005 §1 says the
 * domain is plain, and this is the test that would stop compiling if it were not.
 *
 * <p>Deliberately untagged: the seven parity rules are Issue 2.2's to encode and to tag, and the
 * JaCoCo floor on {@code domain} is measured by the {@code build} job, which excludes the parity
 * group.
 */
class CustomerTest {

	static final Address ADDRESS = new Address("1 Main St", null, "London", "LDN", "N1 1AA", "GB");

	static final ContactInfo CONTACT = new ContactInfo("Ada", "Lovelace", "020 7946 0000", "ada@example.com",
			ADDRESS);

	/** Issue 1.8: a customer is registered with a hash, never a password — see {@link PasswordHashTest}. */
	static final PasswordHash HASH = new PasswordHash(PasswordHashTest.BCRYPT);

	@Test
	@DisplayName("registering creates the account, and the account is active")
	void a_registered_customer_has_an_active_account() {
		Customer customer = Customer.register("ada", HASH, CONTACT);

		assertThat(customer.userId()).isEqualTo("ada");
		assertThat(customer.passwordHash()).isEqualTo(HASH);
		assertThat(customer.account().status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(customer.account().contactInfo()).isEqualTo(CONTACT);
	}

	@Test
	@DisplayName("the aggregate is a value: the same registration twice is the same customer")
	void the_aggregate_has_value_equality() {
		assertThat(Customer.register("ada", HASH, CONTACT)).isEqualTo(Customer.register("ada", HASH, CONTACT));
	}

}
