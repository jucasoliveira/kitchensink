package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ItemDocumentRepository extends MongoRepository<ItemDocument, String> {
    List<ItemDocument> findByProductId(String productId);
}
