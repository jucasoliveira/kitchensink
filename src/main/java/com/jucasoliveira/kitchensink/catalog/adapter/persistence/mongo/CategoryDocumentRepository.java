package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

interface CategoryDocumentRepository extends MongoRepository<CategoryDocument, String> {
}