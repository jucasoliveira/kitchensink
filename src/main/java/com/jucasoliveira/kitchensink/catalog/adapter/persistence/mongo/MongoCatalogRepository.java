package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import com.jucasoliveira.kitchensink.catalog.application.CatalogRepository;
import com.jucasoliveira.kitchensink.catalog.domain.Category;
import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.Product;

@Component
@Profile("mongo")
public class MongoCatalogRepository implements CatalogRepository {
    private static final String PRODUCTS = "products";
    private static final String ITEMS = "items";
    private static final String JOINED = "product";
    private final CategoryDocumentRepository categories;
    private final ProductDocumentRepository products;
    private final ItemDocumentRepository items;
    private final MongoTemplate mongo;

    MongoCatalogRepository(CategoryDocumentRepository categories, ProductDocumentRepository products,
            ItemDocumentRepository items, MongoTemplate mongo) {
        this.categories = categories;
        this.products = products;
        this.items = items;
        this.mongo = mongo;
    }

    @Override
    public Optional<Category> findCategory(String categoryId) {
        return this.categories.findById(categoryId).map(CategoryDocument::toDomain);
    }

    @Override
    public List<Category> findAllCategories() {
        return this.categories.findAll().stream().map(CategoryDocument::toDomain).toList();
    }

    @Override
    public Optional<Product> findProduct(String productId) {
        return this.products.findById(productId).map(ProductDocument::toDomain);
    }

    @Override
    public List<Product> findProductsInCategory(String categoryId) {
        return this.products.findByCategoryId(categoryId).stream().map(ProductDocument::toDomain).toList();
    }

    @Override
    public Optional<Item> findItem(String itemId) {
        return this.items.findById(itemId).map(ItemDocument::toDomain);
    }

    @Override
    public List<Item> findItemsForProduct(String productId) {
        return this.items.findByProductId(productId).stream().map(ItemDocument::toDomain).toList();
    }

    @Override
    public List<Item> searchItems(Collection<String> keywords, String locale) {
        List<String> quoted = keywords.stream().filter(kw -> !kw.isBlank()).map(Pattern::quote).toList();
        if (quoted.isEmpty()) {
            return List.of();
        }

        String itemDetails = "details." + locale;
        String productDetails = JOINED + ".details." + locale;

        List<Criteria> branches = quoted.stream()
                .flatMap(kw -> Stream.of(
                        Criteria.where(productDetails + ".name").regex(kw, "i"),
                        Criteria.where(JOINED + ".categoryId").regex(kw, "i"),
                        Criteria.where(itemDetails + ".description").regex(kw, "i")))
                .toList();

        Aggregation search = Aggregation.newAggregation(
                Aggregation.match(Criteria.where(itemDetails).exists(true)),
                Aggregation.lookup(PRODUCTS, "productId", "_id", JOINED),
                Aggregation.unwind(JOINED),
                Aggregation.match(Criteria.where(productDetails).exists(true)),
                Aggregation.match(new Criteria().orOperator(branches)),
                Aggregation.project("productId", "details"));

        return this.mongo.aggregate(search, ITEMS, ItemDocument.class)
                .getMappedResults()
                .stream()
                .map(ItemDocument::toDomain)
                .toList();
    }
}
