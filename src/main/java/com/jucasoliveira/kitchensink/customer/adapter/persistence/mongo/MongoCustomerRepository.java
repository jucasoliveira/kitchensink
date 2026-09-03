package com.jucasoliveira.kitchensink.customer.adapter.persistence.mongo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;
import com.jucasoliveira.kitchensink.customer.application.DuplicateAccountException;
import com.jucasoliveira.kitchensink.customer.domain.Customer;
import org.springframework.dao.DuplicateKeyException;

@Component
@Profile("mongo")
class MongoCustomerRepository implements CustomerRepository {
    private final CustomerDocumentRepository documents;

    MongoCustomerRepository(CustomerDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public Customer add(Customer customer) {
        try {
            return this.documents.insert(CustomerDocument.from(customer)).toDomain();
        } catch (DuplicateKeyException taken) {
            throw new DuplicateAccountException(customer.userId());
        }
    }

    @Override
    public Optional<Customer> findByUserId(String userId) {
        return documents.findById(userId).map(CustomerDocument::toDomain);
    }

    @Override
    public List<Customer> findAll() {
        return documents.findAll().stream().map(CustomerDocument::toDomain).toList();
    }
}
