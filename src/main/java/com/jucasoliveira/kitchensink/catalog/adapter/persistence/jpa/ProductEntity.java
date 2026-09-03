package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jucasoliveira.kitchensink.catalog.domain.Product;
import com.jucasoliveira.kitchensink.catalog.domain.ProductDetails;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

/**
 * product + product_details — PopulateSQL.xml:86-97.
 *
 * <p>{@code catid} is a plain column rather than a {@code @ManyToOne} to {@link CategoryEntity},
 * for the same reason {@code ProductDocument} holds a plain {@code categoryId}: the port hands
 * back aggregates, and an association here would only be walked to produce one that the caller
 * asks for separately. Keeping it a column also leaves the join in SEARCH_ITEMS explicit, which
 * is how CatalogDAOSQL.xml:112-127 wrote it.
 */
@Entity
@Table(name = "product")
public class ProductEntity {

    @Id
    @Column(name = "productid", length = 10)
    private String id;

    @Column(name = "catid", length = 10)
    private String categoryId;

    @ElementCollection
    @CollectionTable(name = "product_details", joinColumns = @JoinColumn(name = "productid"))
    @MapKeyColumn(name = "locale", length = 10)
    private Map<String, DetailsRow> details = new LinkedHashMap<>();

    protected ProductEntity() {
    }

    ProductEntity(String id, String categoryId, Map<String, DetailsRow> details) {
        this.id = id;
        this.categoryId = categoryId;
        this.details.putAll(details);
    }

    Product toDomain() {
        Map<String, ProductDetails> mapped = new LinkedHashMap<>();
        this.details.forEach(
                (locale, row) -> mapped.put(locale, new ProductDetails(row.name, row.image, row.description)));

        return new Product(this.id, this.categoryId, mapped);
    }

}
