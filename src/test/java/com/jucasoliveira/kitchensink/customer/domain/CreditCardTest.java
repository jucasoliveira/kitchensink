package com.jucasoliveira.kitchensink.customer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 4.1 — {@code CreditCardEJB}'s three CMP fields and the two derived accessors, pinned as
 * characterization.
 *
 * <p>Legacy anchor: {@code components/creditcard/.../ejb/CreditCardEJB.java:50-57} for the fields
 * ({@code cardNumber}, {@code cardType}, {@code expiryDate} — all String), and {@code :88-107} for
 * {@code getExpiryMonth()} / {@code getExpiryYear()}.
 *
 * <p>Finding #4 is why this type exists at all: {@code CreditCardEJB} was declared twice, in
 * {@code components/customer/src/ejb-jar.xml} and {@code components/purchaseorder/src/ejb-jar.xml},
 * because EJB 2.0 had no way to share an entity bean across deployment units. {@code AddressEJB}
 * and {@code ContactInfoEJB} were declared four times each. One record replaces each of them.
 *
 * <p>Tagged {@code parity}: the two defaults below are not a design choice anyone would make
 * today, and the only reason they are here is that the 2003 app behaved this way. If they change,
 * the migration has changed what Pet Store did, and that is exactly what the parity job is for.
 */
@Tag("parity")
class CreditCardTest {

	@Test
	@DisplayName("CreditCardEJB.java:88-107 — expiryDate is one string, split on the slash")
	void the_expiry_date_splits_on_the_slash() {
		// Not a date type: the CMP field is a String and the bean parses it on every read. That is
		// the whole storage format, and the screens rendered month and year from these two calls.
		CreditCard card = new CreditCard("0000-0000-0000-0000", "Visa", "12/2003");

		assertThat(card.expiryMonth()).isEqualTo("12");
		assertThat(card.expiryYear()).isEqualTo("2003");
	}

	@Test
	@DisplayName("CreditCardEJB.java:95,105 — no slash means \"01\" and \"2010\", hardcoded in the 2003 source")
	void a_malformed_expiry_date_falls_back_to_the_legacy_defaults() {
		// getExpiryMonth returns "01" and getExpiryYear returns "2010" when indexOf("/") is -1.
		// Both are literals in the bean. "2010" was seven years in the future when Pet Store 1.3.1
		// shipped and is sixteen years in the past now, so the fallback silently produces an
		// expired card — carried verbatim rather than modernised, because a migration that quietly
		// fixed it would be changing behaviour under cover of a port.
		CreditCard noSlash = new CreditCard(null, null, "122003");

		assertThat(noSlash.expiryMonth()).isEqualTo("01");
		assertThat(noSlash.expiryYear()).isEqualTo("2010");
	}

	@Test
	@DisplayName("CreditCardEJB.java:90,100 — a null expiryDate takes the same fallback, without an NPE")
	void a_null_expiry_date_takes_the_same_fallback() {
		// The legacy guarded with `if (dateString != null)` before calling indexOf, so a card
		// created by AccountEJB.ejbPostCreate — every card in this slice — hits this path on
		// every read rather than throwing.
		assertThat(CreditCard.EMPTY.expiryMonth()).isEqualTo("01");
		assertThat(CreditCard.EMPTY.expiryYear()).isEqualTo("2010");
	}

	@Test
	@DisplayName("a leading or trailing slash yields an empty half, because substring does not check")
	void a_slash_at_either_end_yields_an_empty_half() {
		// substring(0, 0) and substring(slash + 1) on a string that ends at the slash: both are "",
		// not the defaults, because the defaults only fire when there is no slash at all. Pinned
		// because it is the one input where "malformed" and "has a slash" overlap, and a
		// reimplementation that validated the format instead of splitting it would diverge here.
		assertThat(new CreditCard(null, null, "/2003").expiryMonth()).isEmpty();
		assertThat(new CreditCard(null, null, "12/").expiryYear()).isEmpty();
	}

	@Test
	@DisplayName("AccountEJB.java:87-89 — the empty card is a value, not a null: cch.create() made a row")
	void the_empty_card_is_a_row_with_three_null_columns() {
		assertThat(CreditCard.EMPTY.cardNumber()).isNull();
		assertThat(CreditCard.EMPTY.cardType()).isNull();
		assertThat(CreditCard.EMPTY.expiryDate()).isNull();
		assertThat(CreditCard.EMPTY).isEqualTo(new CreditCard(null, null, null));
	}

}
