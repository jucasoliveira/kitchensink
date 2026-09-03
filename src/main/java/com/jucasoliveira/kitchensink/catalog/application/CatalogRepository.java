package com.jucasoliveira.kitchensink.catalog.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.jucasoliveira.kitchensink.catalog.domain.Category;
import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.Product;

public interface CatalogRepository {
    Optional<Category> findCategory(String categoryId);

    List<Category> findAllCategories();

    Optional<Product> findProduct(String productId);

    List<Product> findProductsInCategory(String categoryId);

    Optional<Item> findItem(String itemId);

    List<Item> findItemsForProduct(String productId);

    List<Item> searchItems(Collection<String> keywords, String locale);
}
