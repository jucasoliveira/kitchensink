package com.jucasoliveira.kitchensink.catalog;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Issue 2.1 — the copy of the legacy seed on the test classpath is the legacy seed, byte for byte.
 *
 * <p>The reference tree is git-ignored (146 MB) and never reaches CI, so every parity test reads
 * {@link LegacyCatalogSeed#CLASSPATH_COPY}. A copy can drift — an editor "fixing" whitespace, a
 * stale re-copy after someone touched the original — and a parity baseline that drifted is worse
 * than none. This test closes that gap on every machine that has the original: it is the one
 * place the copy and the evidence meet.
 *
 * <p>In CI the original is absent and the test is <em>skipped with that reason</em>, not passed.
 * That is the only vacuous case, and it is stated in the report rather than hidden.
 */
@Tag("parity")
class LegacySeedCopyIsVerbatimTest {

	@Test
	@DisplayName("src/test/resources/legacy/populate/Populate-UTF8.xml is byte-identical to the reference tree's original")
	void the_copy_matches_the_original() throws IOException {
		assumeTrue(Files.isRegularFile(LegacyCatalogSeed.REFERENCE_TREE_ORIGINAL),
				"reference tree not present (git-ignored, developer machines only) — nothing to compare against");
		byte[] original = Files.readAllBytes(LegacyCatalogSeed.REFERENCE_TREE_ORIGINAL);
		byte[] copy = new ClassPathResource(LegacyCatalogSeed.CLASSPATH_COPY).getContentAsByteArray();
		assertThat(copy).as("the copy must be refreshed with cp, never edited").isEqualTo(original);
	}

}
