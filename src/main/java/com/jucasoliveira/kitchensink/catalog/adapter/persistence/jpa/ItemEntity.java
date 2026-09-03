package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.ItemDetails;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

/**
 * item + item_details — PopulateSQL.xml:140-158.
 *
 * <p>GET_ITEM (CatalogDAOSQL.xml:92) joined these two tables to product and product_details, with
 * {@code b.locale = c.locale} restated as a join predicate, and still returned one locale per
 * execution. Loading the aggregate returns every locale the store holds, and the join survives in
 * exactly one place: the SEARCH_ITEMS statement, where it is a predicate rather than a projection.
 */
@Entity
@Table(name = "item")
public class ItemEntity {

    @Id
    @Column(name = "itemid", length = 10)
    private String id;

    @Column(name = "productid", length = 10)
    private String productId;

    @ElementCollection
    @CollectionTable(name = "item_details", joinColumns = @JoinColumn(name = "itemid"))
    @MapKeyColumn(name = "locale", length = 10)
    private Map<String, ItemDetailsRow> details = new LinkedHashMap<>();

    protected ItemEntity() {
    }

    ItemEntity(String id, String productId, Map<String, ItemDetailsRow> details) {
        this.id = id;
        this.productId = productId;
        this.details.putAll(details);
    }

    Item toDomain() {
        Map<String, ItemDetails> mapped = new LinkedHashMap<>();
        this.details.forEach((locale, row) -> mapped.put(locale, new ItemDetails(row.listPrice, row.unitCost,
                row.attributes(), row.image, row.description)));

        return new Item(this.id, this.productId, mapped);
    }

}
