// src/main/java/com/jucasoliveira/kitchensink/catalog/application/CatalogService.java
package com.jucasoliveira.kitchensink.catalog.application;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;

import org.springframework.stereotype.Service;

import com.jucasoliveira.kitchensink.catalog.domain.Category;
import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.Product;

@Service
public class CatalogService {

    private final CatalogRepository catalog;

    CatalogService(CatalogRepository catalog) {
        this.catalog = catalog;
    }

    public CatalogPage<Category> browseCategories(String locale, int start, int count) {
        List<Category> localized = this.catalog.findAllCategories()
                .stream()
                .filter(category -> category.details().containsKey(locale))
                .sorted(Comparator.comparing(category -> category.details().get(locale).name()))
                .toList();
        return CatalogPage.of(localized, start, count);
    }

    public CatalogPage<Product> browseProducts(String categoryId, String locale, int start, int count) {
        List<Product> localized = this.catalog.findProductsInCategory(categoryId)
                .stream()
                .filter(product -> product.details().containsKey(locale))
                .sorted(Comparator.comparing(product -> product.details().get(locale).name()))
                .toList();
        return CatalogPage.of(localized, start, count);
    }

    public CatalogPage<Item> browseItems(String productId, String locale, int start, int count) {
        return CatalogPage.of(localized(this.catalog.findItemsForProduct(productId), locale), start, count);
    }

    public CatalogPage<Item> search(String query, String locale, int start, int count) {
        Set<String> keywords = keywords(query);
        if (keywords.isEmpty()) {
            return CatalogPage.of(List.of(), start, count);
        }
        return CatalogPage.of(localized(this.catalog.searchItems(keywords, locale), locale), start, count);
    }

    public Optional<Category> category(String categoryId, String locale) {
        return this.catalog.findCategory(categoryId).filter(it -> it.details().containsKey(locale));
    }

    public Optional<Product> product(String productId, String locale) {
        return this.catalog.findProduct(productId).filter(it -> it.details().containsKey(locale));
    }

    public Optional<Item> item(String itemId, String locale) {
        return this.catalog.findItem(itemId).filter(it -> it.details().containsKey(locale));
    }

    static Set<String> keywords(String query) {
        if (query == null) {
            return Set.of();
        }
        StringTokenizer tokenizer = new StringTokenizer(query);
        Set<String> keywords = new java.util.HashSet<>();
        while (tokenizer.hasMoreTokens()) {
            keywords.add(tokenizer.nextToken().toLowerCase(java.util.Locale.ROOT));
        }
        return Set.copyOf(keywords);
    }

    private static List<Item> localized(List<Item> items, String locale) {
        return items.stream()
                .filter(item -> item.details().containsKey(locale))
                .sorted(Comparator.comparing(Item::id))
                .toList();
    }
}