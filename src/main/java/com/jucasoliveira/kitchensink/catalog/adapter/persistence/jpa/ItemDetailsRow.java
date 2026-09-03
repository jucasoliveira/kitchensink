package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One row of item_details — PopulateSQL.xml:146-158.
 *
 * <p>Item.dtd allows {@code Attribute+}, and the legacy flattened them into five columns because
 * SQL has no list type. The same constraint applies here for a different reason: a collection
 * cannot nest inside an {@code @ElementCollection} value. So the flattening comes back, and
 * {@link #attributes()} puts the list together again on the way to the domain.
 */
@Embeddable
public class ItemDetailsRow {

    private static final int COLUMNS = 5;

    @Column(name = "listprice")
    String listPrice;

    @Column(name = "unitcost")
    String unitCost;

    @Column(name = "attr1")
    String attr1;

    @Column(name = "attr2")
    String attr2;

    @Column(name = "attr3")
    String attr3;

    @Column(name = "attr4")
    String attr4;

    @Column(name = "attr5")
    String attr5;

    @Column(name = "image")
    String image;

    @Column(name = "descn") // the legacy column spelling, kept
    String description;

    ItemDetailsRow() {
    }

    ItemDetailsRow(String listPrice, String unitCost, List<String> attributes, String image, String description) {
        this.listPrice = listPrice;
        this.unitCost = unitCost;
        this.attr1 = attribute(attributes, 0);
        this.attr2 = attribute(attributes, 1);
        this.attr3 = attribute(attributes, 2);
        this.attr4 = attribute(attributes, 3);
        this.attr5 = attribute(attributes, 4);
        this.image = image;
        this.description = description;
    }

    List<String> attributes() {
        return Stream.of(this.attr1, this.attr2, this.attr3, this.attr4, this.attr5)
                .filter(Objects::nonNull)
                .toList();
    }

    /** The seed never fills more than two of the five, and never leaves an interior gap. */
    private static String attribute(List<String> attributes, int index) {
        if (attributes == null || index >= Math.min(attributes.size(), COLUMNS)) {
            return null;
        }
        return attributes.get(index);
    }

}
