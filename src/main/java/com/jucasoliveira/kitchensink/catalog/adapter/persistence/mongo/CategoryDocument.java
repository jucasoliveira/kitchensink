package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.jucasoliveira.kitchensink.catalog.domain.Category;
import com.jucasoliveira.kitchensink.catalog.domain.CategoryDetails;

@Document("categories")
public record CategoryDocument(@Id String id, Map<String, DetailsDocument> details) {

    record DetailsDocument(String name, String image, String description) {
    }

    Category toDomain() {
        Map<String, CategoryDetails> mapped = new LinkedHashMap<>();
        this.details
                .forEach((locale, d) -> mapped.put(locale, new CategoryDetails(d.name(), d.image(), d.description())));

        return new Category(this.id, mapped);
    }

}
