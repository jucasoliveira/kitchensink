package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.jucasoliveira.kitchensink.catalog.domain.Product;
import com.jucasoliveira.kitchensink.catalog.domain.ProductDetails;

@Document("products")
public record ProductDocument(@Id String id, String categoryId, Map<String, DetailsDocument> details) {

    record DetailsDocument(String name, String image, String description) {
    }

    Product toDomain() {
        Map<String, ProductDetails> mapped = new LinkedHashMap<>();
        this.details
                .forEach((locale, d) -> mapped.put(locale, new ProductDetails(d.name(), d.image(), d.description())));

        return new Product(this.id, this.categoryId, mapped);
    }

}
