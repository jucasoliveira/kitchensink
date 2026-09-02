package com.jucasoliveira.kitchensink.customer.adapter.persistence.mongo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.Optional;
import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;
import com.jucasoliveira.kitchensink.customer.domain.Customer;

@Component
@Profile("mongo")
class MongoCustomerRepository implements CustomerRepository {
    private final CustomerDocumentRepository documents;

    MongoCustomerRepository(CustomerDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public Customer save(Customer customer) {
        return documents.save(CustomerDocument.from(customer)).toDomain();
    }

    @Override
    public Optional<Customer> findByUserId(String userId) {
        return documents.findById(userId).map(CustomerDocument::toDomain);
    }
}
