package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductDocumentRepository extends MongoRepository<ProductDocument, String> {
    List<ProductDocument> findByCategoryId(String categoryId);
}
