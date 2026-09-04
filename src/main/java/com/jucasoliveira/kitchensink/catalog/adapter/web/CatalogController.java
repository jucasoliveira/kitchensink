package com.jucasoliveira.kitchensink.catalog.adapter.web;

import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.jucasoliveira.kitchensink.catalog.application.CatalogPage;
import com.jucasoliveira.kitchensink.catalog.application.CatalogService;
import com.jucasoliveira.kitchensink.catalog.domain.Category;
import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.Product;

@Controller
@RequestMapping("/catalog")
public class CatalogController {

    /** category.jsp:71-72, product.jsp:71-72 — the <c:otherwise> reset. */
    private static final int DEFAULT_COUNT = 2;

    /** banner.jsp:81,92,103 — the three flags were the whole locale menu. */
    private static final Set<String> SUPPORTED = Set.of("en_US", "ja_JP", "zh_CN");

    private static final String DEFAULT_LOCALE = "en_US";

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    String main() {
        return "catalog/main";
    }

    /**
     * {@code search.screen} — {@code banner.jsp:57-60} posted a {@code keywords} box to it from
     * every page, and {@code mappings.xml} bound it to {@code search.jsp}.
     *
     * <p>Shares {@code CatalogService.search} with the REST resource (3.6), so the two channels
     * cannot drift: the divergences that search carries — {@code %} is a literal here where
     * {@code LIKE} made it a wildcard, and results have no promised order — are the service's, and
     * {@code CatalogSearchContract} pins them once for both.
     */
    @GetMapping("/search")
    String search(@RequestParam(defaultValue = "") String keywords,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "" + DEFAULT_COUNT) int count,
            Locale locale, Model model) {
        String key = localeKey(locale);
        model.addAttribute("keywords", keywords);
        model.addAttribute("page", views(this.catalog.search(keywords, key, start, count),
                item -> ItemView.of(item, this.catalog.product(item.productId(), key).orElseThrow(
                        CatalogController::notFound), key)));
        model.addAttribute("count", count);
        return "catalog/search";
    }

    @GetMapping("/categories/{categoryId}")
    String category(@PathVariable String categoryId,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "" + DEFAULT_COUNT) int count,
            Locale locale, Model model) {
        String key = localeKey(locale);
        Category category = this.catalog.category(categoryId, key).orElseThrow(CatalogController::notFound);
        model.addAttribute("category", CategoryView.of(category, key));
        model.addAttribute("page", views(this.catalog.browseProducts(categoryId, key, start, count),
                product -> ProductView.of(product, key)));
        model.addAttribute("count", count);
        return "catalog/category";
    }

    @GetMapping("/products/{productId}")
    String product(@PathVariable String productId,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "" + DEFAULT_COUNT) int count,
            Locale locale, Model model) {
        String key = localeKey(locale);
        Product product = this.catalog.product(productId, key).orElseThrow(CatalogController::notFound);
        model.addAttribute("product", ProductView.of(product, key));
        model.addAttribute("page", views(this.catalog.browseItems(productId, key, start, count),
                item -> ItemView.of(item, product, key)));
        model.addAttribute("count", count);
        return "catalog/product";
    }

    @GetMapping("/items/{itemId}")
    String item(@PathVariable String itemId, Locale locale, Model model) {
        String key = localeKey(itemId == null ? locale : locale);
        Item item = this.catalog.item(itemId, key).orElseThrow(CatalogController::notFound);
        Product product = this.catalog.product(item.productId(), key).orElseThrow(CatalogController::notFound);
        model.addAttribute("item", ItemView.of(item, product, key));
        return "catalog/item";
    }

    private static <T, V> CatalogPage<V> views(CatalogPage<T> page, Function<T, V> view) {
        return new CatalogPage<>(page.contents().stream().map(view).toList(), page.start(), page.hasNext());
    }

    public static String localeKey(Locale locale) {
        String key = locale.toString();
        return SUPPORTED.contains(key) ? key : DEFAULT_LOCALE;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}