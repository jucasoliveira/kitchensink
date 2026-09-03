package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jucasoliveira.kitchensink.catalog.domain.Category;
import com.jucasoliveira.kitchensink.catalog.domain.CategoryDetails;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

/**
 * category + category_details — PopulateSQL.xml:60-70.
 *
 * <p>The two tables the Mongo adapter collapsed into one document are back, under the names the
 * legacy gave them: {@code @MapKeyColumn("locale")} is the {@code category_details.locale} column
 * every statement in CatalogDAOSQL.xml filtered on.
 *
 * <p>{@code CategoryDocument} is a record. This cannot be one — JPA requires a no-arg constructor
 * and a non-final class — which is the clearest small illustration of what the relational mapping
 * costs that the document mapping does not.
 */
@Entity
@Table(name = "category")
public class CategoryEntity {

    @Id
    @Column(name = "catid", length = 10)
    private String id;

    @ElementCollection
    @CollectionTable(name = "category_details", joinColumns = @JoinColumn(name = "catid"))
    @MapKeyColumn(name = "locale", length = 10)
    private Map<String, DetailsRow> details = new LinkedHashMap<>();

    protected CategoryEntity() {
    }

    CategoryEntity(String id, Map<String, DetailsRow> details) {
        this.id = id;
        this.details.putAll(details);
    }

    Category toDomain() {
        Map<String, CategoryDetails> mapped = new LinkedHashMap<>();
        this.details.forEach(
                (locale, row) -> mapped.put(locale, new CategoryDetails(row.name, row.image, row.description)));

        return new Category(this.id, mapped);
    }

}
