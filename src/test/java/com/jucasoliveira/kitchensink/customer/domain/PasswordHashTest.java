package com.jucasoliveira.kitchensink.customer.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Issue 1.8 — the one deliberate deviation from parity, made unrepresentable.
 *
 * <p>Issue 2.2: the BCrypt deviation's {@code @Tag("parity")} test lives in
 * {@code SignOnTest#the_password_never_reaches_the_store}, not here. This class is the primary,
 * and only, exerciser of {@link PasswordHash}'s branches, and every method in it is in the
 * {@code build} job's JaCoCo bundle; tagging any of them {@code parity} would route them to the
 * {@code -DexcludedGroups=parity} run instead and drop domain coverage under the 0.70 branch
 * floor — verified by trying it and watching CI fail on exactly that.
 *
 * <p>Legacy: {@code UserEJB} keeps the password as a CMP {@code String} field and compares it with
 * {@code password.equals(getPassword())} ({@code signon/.../user/ejb/UserEJB.java:88}) — finding #1
 * in {@code docs/01-legacy-architecture.md}, and the deviation ADR-0006 lists first. The type that
 * replaces that field refuses to hold anything that is not already a BCrypt hash, so the plaintext
 * path is not merely unused: it does not compile. That is the acceptance criterion "no plaintext
 * credential path ever exists in this repository's history", stated as a constructor.
 *
 * <p>Pure Java. The domain does not know Spring Security exists (LayeringRulesTest); the hashing
 * happens one layer out, in {@code CustomerRegistration}, and only the result crosses this line.
 */
class PasswordHashTest {

	/** {@code $2a$10$} + 22 chars of salt + 31 chars of digest: the modular-crypt BCrypt form. */
	static final String BCRYPT = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

	@Test
	@DisplayName("a BCrypt hash is accepted as-is")
	void a_bcrypt_hash_is_accepted() {
		assertThat(new PasswordHash(BCRYPT).value()).isEqualTo(BCRYPT);
	}

	@ParameterizedTest(name = "{0} is refused")
	@ValueSource(strings = { "$2b$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
			"$2y$04$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy" })
	@DisplayName("the 2b and 2y revisions are the same algorithm and are accepted too")
	void the_other_bcrypt_revisions_are_accepted(String hash) {
		assertThat(new PasswordHash(hash).value()).isEqualTo(hash);
	}

	@ParameterizedTest(name = "\"{0}\" is refused")
	@ValueSource(strings = { "secret", "", "   ",
			// the legacy seed's own credential (populate/Populate-UTF8.xml:74) — the exact string
			// UserEJB stored, and the one this type must never accept
			"j2ee",
			// Spring's DelegatingPasswordEncoder form: a hash, but not the bare one this slice stores
			"{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
			"$2a$10$tooshort", "$1$md5crypt$abcdefghijklmnopqrstuv",
			"5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8" })
	@DisplayName("a plaintext password, a prefixed hash or any other digest is refused")
	void anything_that_is_not_a_bcrypt_hash_is_refused(String notAHash) {
		assertThatIllegalArgumentException().isThrownBy(() -> new PasswordHash(notAHash));
	}

	@Test
	@DisplayName("null is refused rather than stored as an empty credential")
	void null_is_refused() {
		assertThatIllegalArgumentException().isThrownBy(() -> new PasswordHash(null));
	}

	@Test
	@DisplayName("the hash does not print: a record's default toString would put it in every log line")
	void the_hash_does_not_appear_in_to_string() {
		// CreateUserServlet.java:69 printed the plaintext password to stdout on every registration.
		// A hash is not a password, but it is still the thing an attacker wants offline, and a
		// Customer that ends up in a log message should not carry it.
		assertThat(new PasswordHash(BCRYPT).toString()).doesNotContain(BCRYPT);
	}

	@Test
	@DisplayName("two hashes of the same value are equal: it is a value object")
	void equality_is_by_value() {
		assertThat(new PasswordHash(BCRYPT)).isEqualTo(new PasswordHash(BCRYPT));
	}

}
