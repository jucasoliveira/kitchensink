package com.jucasoliveira.kitchensink.customer.application;

import java.util.Set;
import java.util.stream.Collectors;

import com.jucasoliveira.kitchensink.customer.domain.Address;
import com.jucasoliveira.kitchensink.customer.domain.ContactInfo;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 1.8 — Jakarta Validation replaces the hand-rolled checks, rule for rule.
 *
 * <p>The legacy application validated registration input in two places, by hand:
 * <ul>
 * <li>{@code signon/.../user/ejb/UserEJB.java:64-76} ({@code ejbCreate}): the user id and the
 * password are each at most 25 characters ({@code UserLocal.MAX_USERID_LENGTH},
 * {@code MAX_PASSWD_LENGTH}, {@code UserLocal.java:46-47}), and the user id may not contain
 * {@code %} or {@code *} — the SQL {@code LIKE} wildcards, because the id is a CMP primary key
 * that finders match on.</li>
 * <li>{@code petstore/.../web/actions/CustomerHTMLAction.java:182-253}
 * ({@code extractContactInfo}): last name, first name, street address, city, state, postal code
 * and telephone are required; address line 2, country and e-mail are not. Every missing field
 * was collected into a {@code MissingFormDataException} and stuffed into a request attribute for
 * the JSP to find.</li>
 * </ul>
 *
 * <p>Each of those becomes a constraint annotation on the type whose value it constrains: the
 * transient registration input ({@code userId}, {@code password}) on
 * {@link RegisterCustomerCommand}, the persisted invariants on the domain value objects, and
 * {@code @Valid} cascading from one to the other. The assertions are on <em>property paths</em>,
 * because a path per field is what lets Issue 1.9 render each violation next to its input — the
 * legacy's {@code missingFields} list, without the hand-rolled plumbing.
 *
 * <p>No Spring context: the validator is the plain Jakarta one, so this runs in milliseconds and
 * proves the constraints belong to the types rather than to any framework wiring around them.
 */
class RegisterCustomerCommandTest {

	static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	static final Address ADDRESS = new Address("1 Main St", null, "London", "LDN", "N1 1AA", "GB");

	static final ContactInfo CONTACT = new ContactInfo("Ada", "Lovelace", "020 7946 0000", "ada@example.com",
			ADDRESS);

	static final String TWENTY_FIVE = "a".repeat(25);

	static final String TWENTY_SIX = "a".repeat(26);

	@Test
	@DisplayName("a complete registration has no violations")
	void a_complete_command_is_valid() {
		assertThat(violations(new RegisterCustomerCommand("ada", "s3cret", CONTACT))).isEmpty();
	}

	@Test
	@DisplayName("UserEJB.java:64 — a user id is at most 25 characters, and 25 is allowed")
	void the_user_id_is_at_most_25_characters() {
		assertThat(violations(new RegisterCustomerCommand(TWENTY_FIVE, "s3cret", CONTACT))).isEmpty();
		assertThat(paths(new RegisterCustomerCommand(TWENTY_SIX, "s3cret", CONTACT))).containsExactly("userId");
	}

	@Test
	@DisplayName("UserEJB.java:68 — a password is at most 25 characters, and 25 is allowed")
	void the_password_is_at_most_25_characters() {
		assertThat(violations(new RegisterCustomerCommand("ada", TWENTY_FIVE, CONTACT))).isEmpty();
		assertThat(paths(new RegisterCustomerCommand("ada", TWENTY_SIX, CONTACT))).containsExactly("password");
	}

	@Test
	@DisplayName("UserEJB.java:72-76 — a user id may not contain the LIKE wildcards % or *")
	void the_user_id_may_not_contain_sql_wildcards() {
		assertThat(paths(new RegisterCustomerCommand("ada%", "s3cret", CONTACT))).containsExactly("userId");
		assertThat(paths(new RegisterCustomerCommand("a*da", "s3cret", CONTACT))).containsExactly("userId");
	}

	@Test
	@DisplayName("a user id and a password are required — the legacy would have NPE'd on the null and stored the blank")
	void the_user_id_and_password_are_required() {
		// UserEJB.ejbCreate:64 calls userName.length() with no null check, and an empty string
		// passes every check it has. Neither is a credential; both are refused here.
		assertThat(paths(new RegisterCustomerCommand(null, null, CONTACT))).containsExactlyInAnyOrder("userId",
				"password");
		assertThat(paths(new RegisterCustomerCommand("  ", "  ", CONTACT))).containsExactlyInAnyOrder("userId",
				"password");
	}

	@Test
	@DisplayName("CustomerHTMLAction.java:185-198, 234 — names and telephone are required")
	void names_and_telephone_are_required() {
		ContactInfo blank = new ContactInfo(" ", "", null, "ada@example.com", ADDRESS);

		assertThat(paths(new RegisterCustomerCommand("ada", "s3cret", blank))).containsExactlyInAnyOrder(
				"contactInfo.givenName", "contactInfo.familyName", "contactInfo.telephone");
	}

	@Test
	@DisplayName("CustomerHTMLAction.java:241-244 — e-mail is optional, but if given it is an e-mail")
	void email_is_optional_but_well_formed() {
		ContactInfo noEmail = new ContactInfo("Ada", "Lovelace", "020 7946 0000", null, ADDRESS);
		ContactInfo badEmail = new ContactInfo("Ada", "Lovelace", "020 7946 0000", "not-an-email", ADDRESS);

		assertThat(violations(new RegisterCustomerCommand("ada", "s3cret", noEmail))).isEmpty();
		assertThat(paths(new RegisterCustomerCommand("ada", "s3cret", badEmail)))
			.containsExactly("contactInfo.email");
	}

	@Test
	@DisplayName("CustomerHTMLAction.java:199-229 — street, city, state and postal code are required")
	void the_address_lines_the_legacy_required_are_required() {
		Address blank = new Address("", null, " ", null, "", "GB");
		ContactInfo contact = new ContactInfo("Ada", "Lovelace", "020 7946 0000", "ada@example.com", blank);

		assertThat(paths(new RegisterCustomerCommand("ada", "s3cret", contact))).containsExactlyInAnyOrder(
				"contactInfo.address.streetName1", "contactInfo.address.city", "contactInfo.address.state",
				"contactInfo.address.zipCode");
	}

	@Test
	@DisplayName("CustomerHTMLAction.java:206-209, 232 — address line 2 and country are optional")
	void address_line_2_and_country_are_optional() {
		Address minimal = new Address("1 Main St", null, "London", "LDN", "N1 1AA", null);
		ContactInfo contact = new ContactInfo("Ada", "Lovelace", "020 7946 0000", "ada@example.com", minimal);

		assertThat(violations(new RegisterCustomerCommand("ada", "s3cret", contact))).isEmpty();
	}

	@Test
	@DisplayName("the contact info and its address are required as a whole, not just field by field")
	void contact_info_and_address_are_required() {
		assertThat(paths(new RegisterCustomerCommand("ada", "s3cret", null))).containsExactly("contactInfo");

		ContactInfo noAddress = new ContactInfo("Ada", "Lovelace", "020 7946 0000", "ada@example.com", null);
		assertThat(paths(new RegisterCustomerCommand("ada", "s3cret", noAddress)))
			.containsExactly("contactInfo.address");
	}

	@Test
	@DisplayName("every violation is reported at once, the way the legacy's missingFields list was")
	void all_violations_are_reported_together() {
		// CustomerHTMLAction collected every missing field before failing (:184-246), so the user
		// saw one list rather than one error per round trip. Bean Validation does the same.
		ContactInfo blank = new ContactInfo("", "", "", "nope", new Address("", null, "", "", "", null));

		assertThat(paths(new RegisterCustomerCommand(TWENTY_SIX, "", blank))).hasSize(10);
	}

	static Set<ConstraintViolation<RegisterCustomerCommand>> violations(RegisterCustomerCommand command) {
		return VALIDATOR.validate(command);
	}

	static Set<String> paths(RegisterCustomerCommand command) {
		return violations(command).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
	}

}
