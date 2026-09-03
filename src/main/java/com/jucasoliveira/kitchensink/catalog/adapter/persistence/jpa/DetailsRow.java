package com.jucasoliveira.kitchensink.catalog.adapter.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One row of category_details / product_details — PopulateSQL.xml:66-70, 92-97.
 */
@Embeddable
public class DetailsRow {

    @Column(name = "name")
    String name;

    @Column(name = "image")
    String image;

    @Column(name = "descn") // the legacy column spelling, kept
    String description;

    DetailsRow() {
    }

    DetailsRow(String name, String image, String description) {
        this.name = name;
        this.image = image;
        this.description = description;
    }
}