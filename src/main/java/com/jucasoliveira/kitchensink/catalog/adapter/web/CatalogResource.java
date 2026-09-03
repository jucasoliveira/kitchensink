package com.jucasoliveira.kitchensink.catalog.adapter.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.jucasoliveira.kitchensink.catalog.application.CatalogService;
import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.Product;

@RestController
@RequestMapping(path = "/api/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
public class CatalogResource {

    /**
     * CatalogHelper.java:82-83 — the defaults the JSPs reset to when no param was
     * supplied.
     */
    static final String START = "0";
    static final String COUNT = "2";

    /**
     * category.jsp:75; ja/category.jsp:73 and zh/category.jsp:73 hardcode their
     * own.
     */
    static final String LOCALE = "en_US";

    private final CatalogService catalog;

    CatalogResource(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    PageView<CategoryView> categories(@RequestParam(defaultValue = LOCALE) String locale,
            @RequestParam(defaultValue = START) int start,
            @RequestParam(defaultValue = COUNT) int count) {
        return PageView.of(this.catalog.browseCategories(locale, start, count), it -> CategoryView.of(it, locale));
    }

    @GetMapping("/categories/{categoryId}")
    CategoryView category(@PathVariable String categoryId, @RequestParam(defaultValue = LOCALE) String locale) {
        return CategoryView.of(found(this.catalog.category(categoryId, locale), "category", categoryId, locale),
                locale);
    }

    @GetMapping("/categories/{categoryId}/products")
    PageView<ProductView> products(@PathVariable String categoryId,
            @RequestParam(defaultValue = LOCALE) String locale,
            @RequestParam(defaultValue = START) int start,
            @RequestParam(defaultValue = COUNT) int count) {
        found(this.catalog.category(categoryId, locale), "category", categoryId, locale);
        return PageView.of(this.catalog.browseProducts(categoryId, locale, start, count),
                it -> ProductView.of(it, locale));
    }

    @GetMapping("/products/{productId}")
    ProductView product(@PathVariable String productId, @RequestParam(defaultValue = LOCALE) String locale) {
        return ProductView.of(found(this.catalog.product(productId, locale), "product", productId, locale), locale);
    }

    @GetMapping("/products/{productId}/items")
    PageView<ItemView> items(@PathVariable String productId,
            @RequestParam(defaultValue = LOCALE) String locale,
            @RequestParam(defaultValue = START) int start,
            @RequestParam(defaultValue = COUNT) int count) {
        Product product = found(this.catalog.product(productId, locale), "product", productId, locale);
        return PageView.of(this.catalog.browseItems(productId, locale, start, count),
                it -> ItemView.of(it, product, locale));
    }

    @GetMapping("/items/{itemId}")
    ItemView item(@PathVariable String itemId, @RequestParam(defaultValue = LOCALE) String locale) {
        Item item = found(this.catalog.item(itemId, locale), "item", itemId, locale);
        return ItemView.of(item, productOf(item, locale), locale);
    }

    @GetMapping("/search")
    PageView<ItemView> search(@RequestParam(defaultValue = "") String keywords,
            @RequestParam(defaultValue = LOCALE) String locale,
            @RequestParam(defaultValue = START) int start,
            @RequestParam(defaultValue = COUNT) int count) {
        return PageView.of(this.catalog.search(keywords, locale, start, count),
                it -> ItemView.of(it, productOf(it, locale), locale));
    }

    private Product productOf(Item item, String locale) {
        return found(this.catalog.product(item.productId(), locale), "product", item.productId(), locale);
    }

    private static <T> T found(java.util.Optional<T> it, String kind, String id, String locale) {
        return it.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "no %s %s in %s".formatted(kind, id, locale)));
    }
}