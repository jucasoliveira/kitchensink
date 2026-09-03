package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.jucasoliveira.kitchensink.catalog.application.CatalogRepository;
import com.jucasoliveira.kitchensink.catalog.domain.Category;
import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("jpa")
@Transactional(readOnly = true)
public class JpaCatalogRepository implements CatalogRepository {

    private final CategoryEntityRepository categories;
    private final ProductEntityRepository products;
    private final ItemEntityRepository items;

    @PersistenceContext
    private EntityManager entities;

    JpaCatalogRepository(CategoryEntityRepository categories, ProductEntityRepository products,
            ItemEntityRepository items) {
        this.categories = categories;
        this.products = products;
        this.items = items;
    }

    @Override
    public Optional<Category> findCategory(String categoryId) {
        return this.categories.findById(categoryId).map(CategoryEntity::toDomain);
    }

    @Override
    public List<Category> findAllCategories() {
        return this.categories.findAll().stream().map(CategoryEntity::toDomain).toList();
    }

    @Override
    public Optional<Product> findProduct(String productId) {
        return this.products.findById(productId).map(ProductEntity::toDomain);
    }

    @Override
    public List<Product> findProductsInCategory(String categoryId) {
        return this.products.findByCategoryId(categoryId).stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public Optional<Item> findItem(String itemId) {
        return this.items.findById(itemId).map(ItemEntity::toDomain);
    }

    @Override
    public List<Item> findItemsForProduct(String productId) {
        return this.items.findByProductId(productId).stream().map(ItemEntity::toDomain).toList();
    }

    // SEARCH_ITEMS (CatalogDAOSQL.xml:112-127). The keyword count is not known
    // until the request
    // arrives, so the statement is assembled per call — which is exactly what
    // GenericCatalogDAO.java:343-395 did with the occurrence="VARIABLE" fragment.
    @Override
    public List<Item> searchItems(Collection<String> keywords, String locale) {
        List<String> patterns = keywords.stream()
                .filter(keyword -> !keyword.isBlank())
                .map(JpaCatalogRepository::pattern)
                .toList();
        if (patterns.isEmpty()) {
            return List.of();
        }

        TypedQuery<ItemEntity> query = this.entities.createQuery(statement(patterns.size()), ItemEntity.class);
        query.setParameter("locale", locale);
        for (int i = 0; i < patterns.size(); i++) {
            query.setParameter("kw" + i, patterns.get(i));
        }
        return query.getResultList().stream().map(ItemEntity::toDomain).toList();
    }

    private static final String HEAD = """
            select distinct i from ItemEntity i
              join i.details idet
              join ProductEntity p on p.id = i.productId
              join p.details pdet
             where key(idet) = :locale and key(pdet) = :locale
               and (""";

    private static String statement(int keywords) {
        StringBuilder jpql = new StringBuilder(HEAD);
        for (int i = 0; i < keywords; i++) {
            jpql.append(i == 0 ? "" : " or ").append(branch(i));
        }
        return jpql.append(")").toString();
    }

    /** The three OR'd columns of CatalogDAOSQL.xml:119-121, once per keyword. */
    private static String branch(int i) {
        String keyword = ":kw" + i;
        return "(lower(pdet.name) like " + keyword + " escape '!'"
                + " or lower(p.categoryId) like " + keyword + " escape '!'"
                + " or lower(idet.description) like " + keyword + " escape '!')";
    }

    /**
     * '%' + keyword + '%' (GenericCatalogDAO.java:361-365), with the wildcards
     * neutralised.
     */
    private static String pattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}