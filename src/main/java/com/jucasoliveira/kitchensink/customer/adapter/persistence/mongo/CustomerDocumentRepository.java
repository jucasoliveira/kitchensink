package com.jucasoliveira.kitchensink.customer.adapter.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

interface CustomerDocumentRepository extends MongoRepository<CustomerDocument, String> {
}
