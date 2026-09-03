package com.jucasoliveira.kitchensink.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Issue 2.1 — the legacy catalog seed, read exactly as the 2003 loader read it.
 *
 * <p>Legacy anchor: {@code apps/petstore/src/docroot/populate/Populate-UTF8.xml:204-1198} (the
 * {@code <Catalog>} element) as consumed by {@code tools/populate/PopulateServlet.java} through
 * the {@code CategoryPopulator → ProductPopulator → ItemPopulator} SAX filter chain, each with its
 * {@code *DetailsPopulator} writing the per-locale row.
 *
 * <p>This is a <em>test fixture</em>, not a loader. It exists so that the parity tests can state
 * "the store holds what the legacy seed holds" against the legacy file itself, rather than against
 * a number someone typed. Two rules of the legacy loader are reproduced here, because a fixture
 * that mis-read the seed would pin the wrong baseline:
 *
 * <ol>
 * <li><b>Locale keys are stored with an underscore.</b> The seed says {@code xml:lang="en-US"};
 * {@code XMLDBHandler.java:179-180} ({@code normalizeValue}) rewrites {@code -} to {@code _} on the
 * way in, so the {@code *_details.locale} columns hold {@code en_US}, {@code ja_JP}, {@code zh_CN}
 * — the {@code Locale.toString()} form the catalog DAO later queries with.</li>
 * <li><b>A details row is one per locale, and a locale may be missing.</b> The DTDs say
 * {@code (CategoryDetails+)}, {@code (ProductDetails+)}, {@code (ItemDetails+)} — one or more, not
 * exactly three. Nothing in the loader fills a gap, so a missing locale row stays missing.</li>
 * </ol>
 *
 * <h2>Where the file comes from</h2>
 *
 * <p>The reference tree {@code petstore1.3.1_02/} is 146 MB, git-ignored, and exists only on a
 * developer's machine — CI never has it. So the seed is read from a <em>verbatim copy</em> at
 * {@code src/test/resources/legacy/populate/Populate-UTF8.xml}, and
 * {@link LegacySeedCopyIsVerbatimTest} asserts, wherever the reference tree is present, that the
 * copy is byte-identical to it. The evidence is copied, never edited ({@code AGENTS.md} §2).
 *
 * <p>The copy is parsed without its DTDs: the DOCTYPE pulls in {@code dtds/*.dtd} by relative
 * path, nothing here validates, and resolving every external entity to an empty document keeps
 * the parser off the file system entirely.
 */
public final class LegacyCatalogSeed {

	/** The verbatim copy on the test classpath — what every test reads. */
	public static final String CLASSPATH_COPY = "legacy/populate/Populate-UTF8.xml";

	/** The original in the git-ignored reference tree — present locally, absent in CI. */
	public static final Path REFERENCE_TREE_ORIGINAL = Path.of("petstore1.3.1_02", "src", "apps", "petstore",
			"src", "docroot", "populate", "Populate-UTF8.xml");

	/** {@code category_details}: {@code (catid, name, image, descn, locale)} — PopulateSQL.xml:66-70. */
	public record Details(String name, String image, String description) {
	}

	/** {@code item_details}: PopulateSQL.xml:146-158 — up to five attributes, flattened as columns. */
	public record ItemDetails(String listPrice, String unitCost, List<String> attributes, String image, String description) {
	}

	public record Category(String id, Map<String, Details> details) {
	}

	public record Product(String id, String categoryId, Map<String, Details> details) {
	}

	public record Item(String id, String productId, Map<String, ItemDetails> details) {
	}

	public final List<Category> categories = new ArrayList<>();
	public final List<Product> products = new ArrayList<>();
	public final List<Item> items = new ArrayList<>();

	/** Reads the classpath copy. */
	public static LegacyCatalogSeed read() {
		try (InputStream in = new ClassPathResource(CLASSPATH_COPY).getInputStream()) {
			return read(in);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not read the legacy seed copy " + CLASSPATH_COPY, ex);
		}
	}

	static LegacyCatalogSeed read(InputStream populateUtf8) {
		try {
			Element populate = parser().parse(populateUtf8).getDocumentElement();
			Element catalog = child(populate, "Catalog");
			LegacyCatalogSeed seed = new LegacyCatalogSeed();
			for (Element e : children(child(catalog, "Categories"), "Category")) {
				seed.categories.add(new Category(e.getAttribute("id"), details(e, "CategoryDetails")));
			}
			for (Element e : children(child(catalog, "Products"), "Product")) {
				seed.products.add(new Product(e.getAttribute("id"), e.getAttribute("category"),
						details(e, "ProductDetails")));
			}
			for (Element e : children(child(catalog, "Items"), "Item")) {
				Map<String, ItemDetails> details = new LinkedHashMap<>();
				for (Element d : children(e, "ItemDetails")) {
					List<String> attributes = children(d, "Attribute").stream().map(Element::getTextContent)
							.map(String::trim).toList();
					details.put(locale(d), new ItemDetails(text(d, "ListPrice"), text(d, "UnitCost"), attributes,
							text(d, "Image"), text(d, "Description")));
				}
				seed.items.add(new Item(e.getAttribute("id"), e.getAttribute("product"), details));
			}
			return seed;
		}
		catch (IOException | ParserConfigurationException | SAXException ex) {
			throw new IllegalStateException("Could not parse the legacy seed", ex);
		}
	}

	private static DocumentBuilder parser() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		DocumentBuilder builder = factory.newDocumentBuilder();
		// Every SYSTEM "dtds/*.dtd" reference resolves to nothing: no validation, no file access.
		builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
		return builder;
	}

	/** Rows of {@code *_details} for one entity, keyed by the locale as the legacy stored it. */
	private static Map<String, Details> details(Element entity, String tag) {
		Map<String, Details> details = new LinkedHashMap<>();
		for (Element d : children(entity, tag)) {
			details.put(locale(d), new Details(text(d, "Name"), text(d, "Image"), text(d, "Description")));
		}
		return details;
	}

	/** {@code XMLDBHandler.java:179-180}: {@code xml:lang="en-US"} is stored as {@code en_US}. */
	static String locale(Element details) {
		return details.getAttributeNS(XMLConstants.XML_NS_URI, "lang").replace('-', '_');
	}

	private static Element child(Element parent, String tag) {
		List<Element> found = children(parent, tag);
		if (found.size() != 1) {
			throw new IllegalStateException("Expected one <" + tag + "> under <" + parent.getTagName() + ">, found "
					+ found.size());
		}
		return found.get(0);
	}

	private static List<Element> children(Element parent, String tag) {
		List<Element> found = new ArrayList<>();
		NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			if (nodes.item(i) instanceof Element e && e.getTagName().equals(tag)) {
				found.add(e);
			}
		}
		return found;
	}

	/** Text of an optional child element, {@code null} when absent — the legacy columns are nullable. */
	private static String text(Element parent, String tag) {
		List<Element> found = children(parent, tag);
		return found.isEmpty() ? null : found.get(0).getTextContent().trim();
	}

}
