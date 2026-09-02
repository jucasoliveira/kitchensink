package com.jucasoliveira.kitchensink;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

/**
 * Test-side reader for {@code src/main/resources/application.yaml}: one file, several documents,
 * split on {@code ---} and selected by {@code spring.config.activate.on-profile}.
 *
 * <p>It exists so that {@link ComposeConsistencyTest} and {@link ProfileConfigurationTest} can
 * assert on the file <em>as text</em>, without a Spring context. Boot's own binder would resolve
 * placeholders, apply the active profile and merge the documents — which is exactly the processing
 * these tests want to look underneath. What the application actually does with the file is the job
 * of {@link PersistenceProfileMongoTest} and {@link PersistenceProfileJpaTest}.
 *
 * <p>Nested maps are flattened to dotted keys ({@code spring.mongodb.uri}); lists are kept as lists.
 * {@link #value(String)} resolves {@code ${NAME:default}} placeholders to their default, because
 * that default is what a clean clone boots with; {@link #raw(String)} returns the text as written.
 */
final class ApplicationYaml {

	/** The repository root, found by walking up from the working directory to {@code compose.yaml}. */
	static final Path PROJECT_ROOT = projectRoot();

	private static final Path FILE = PROJECT_ROOT.resolve("src/main/resources/application.yaml");

	private static final String ON_PROFILE = "spring.config.activate.on-profile";

	private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}");

	private final String name;

	private final Map<String, Object> properties;

	private ApplicationYaml(String name, Map<String, Object> properties) {
		this.name = name;
		this.properties = properties;
	}

	/** The document with no {@code on-profile}: it applies whatever profile is active. */
	static ApplicationYaml defaults() {
		return document(null);
	}

	/** The document activated by the given profile, and nothing from any other document. */
	static ApplicationYaml profile(String profile) {
		return document(profile);
	}

	/** Every document, in file order — for assertions that must hold across all of them. */
	static List<ApplicationYaml> allDocuments() {
		List<ApplicationYaml> all = new ArrayList<>();
		for (Map<String, Object> document : documents()) {
			Object profile = document.get(ON_PROFILE);
			all.add(new ApplicationYaml(profile == null ? "defaults" : "profile " + profile, document));
		}
		return all;
	}

	Set<String> keys() {
		return this.properties.keySet();
	}

	/** The value as written in the file, placeholders included; {@code null} if absent. */
	String raw(String key) {
		Object value = this.properties.get(key);
		return value == null ? null : String.valueOf(value);
	}

	/**
	 * The value a clean clone boots with: {@code ${NAME:default}} becomes {@code default}. A
	 * placeholder without a default is an error here, because it is one at startup too.
	 */
	String value(String key) {
		String raw = raw(key);
		if (raw == null) {
			return null;
		}
		Matcher matcher = PLACEHOLDER.matcher(raw);
		StringBuilder resolved = new StringBuilder();
		while (matcher.find()) {
			if (matcher.group(2) == null) {
				throw new AssertionError(key + " in the " + this.name + " document of application.yaml uses ${"
						+ matcher.group(1) + "} with no default, so a clean clone cannot boot without it");
			}
			matcher.appendReplacement(resolved, Matcher.quoteReplacement(matcher.group(2)));
		}
		matcher.appendTail(resolved);
		return resolved.toString();
	}

	List<String> list(String key) {
		Object value = this.properties.get(key);
		if (value == null) {
			return List.of();
		}
		if (value instanceof List<?> items) {
			return items.stream().map(String::valueOf).toList();
		}
		// A single scalar is a one-element list to Boot's binder as well.
		return List.of(String.valueOf(value));
	}

	@Override
	public String toString() {
		return "application.yaml [" + this.name + "]";
	}

	private static ApplicationYaml document(String profile) {
		List<Map<String, Object>> matching = documents().stream()
			.filter(document -> Objects.equals(profile, document.get(ON_PROFILE)))
			.toList();
		String name = profile == null ? "defaults" : "profile " + profile;
		if (matching.size() != 1) {
			throw new AssertionError("expected exactly one " + name + " document in " + FILE + ", found "
					+ matching.size());
		}
		return new ApplicationYaml(name, matching.get(0));
	}

	private static List<Map<String, Object>> documents() {
		try (Reader reader = Files.newBufferedReader(FILE)) {
			List<Map<String, Object>> flattened = new ArrayList<>();
			for (Object document : new Yaml().loadAll(reader)) {
				if (document != null) {
					Map<String, Object> flat = new LinkedHashMap<>();
					flatten("", document, flat);
					flattened.add(flat);
				}
			}
			return flattened;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void flatten(String prefix, Object value, Map<String, Object> into) {
		if (value instanceof Map<?, ?> map) {
			map.forEach((key, nested) -> flatten(prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key,
					nested, into));
		}
		else {
			into.put(prefix, value);
		}
	}

	/**
	 * Walks up from the working directory rather than trusting it. Surefire runs from the project
	 * basedir, but this project moves {@code build.directory} off the volume it lives on, and a
	 * test that reads repository files should not be the thing that quietly breaks next time
	 * something like that changes.
	 */
	private static Path projectRoot() {
		Path directory = Path.of("").toAbsolutePath();
		while (directory != null && !Files.exists(directory.resolve("compose.yaml"))) {
			directory = directory.getParent();
		}
		if (directory == null) {
			throw new IllegalStateException("no compose.yaml above " + Path.of("").toAbsolutePath());
		}
		return directory;
	}

}
