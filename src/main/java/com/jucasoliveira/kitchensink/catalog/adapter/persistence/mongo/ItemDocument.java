package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.ItemDetails;

@Document("items")
record ItemDocument(@Id String id, String productId, Map<String, ItemDetailsDocument> details) {

    record ItemDetailsDocument(String listPrice, String unitCost, List<String> attributes, String image,
            String description) {
    }

    Item toDomain() {
        Map<String, ItemDetails> mapped = new LinkedHashMap<>();
        this.details.forEach((locale, d) -> mapped.put(locale,
                new ItemDetails(d.listPrice(), d.unitCost(), d.attributes(), d.image(), d.description())));

        return new Item(this.id, this.productId, mapped);
    }

}
