package com.jucasoliveira.kitchensink.customer.adapter.persistence.jpa;

import java.util.List;
import java.util.Optional;

import com.jucasoliveira.kitchensink.customer.application.CustomerRepository;
import com.jucasoliveira.kitchensink.customer.application.DuplicateAccountException;
import com.jucasoliveira.kitchensink.customer.domain.Customer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The second adapter behind {@link CustomerRepository} — issue 4.6.
 *
 * <p>Its whole job is to make the profile switch true for the customer slice as issue 3.3 made it
 * true for the catalog: one port, two adapters, and {@code --spring.profiles.active} the only
 * thing deciding which store answers. That the two answer the <em>same</em> questions is
 * {@code CustomerRepositoryContract}'s to prove — 15 assertions, run once per profile — not this
 * class's to assert.
 *
 * <p>Landing it is what lets the five {@code @Profile("mongo")} guards come off the service, the
 * {@code UserDetailsService}, the two web adapters and the sign-on success handler, which is the
 * same clean-up issue 3.3 did to {@code CatalogService}.
 */
@Component
@Profile("jpa")
@Transactional
class JpaCustomerRepository implements CustomerRepository {

    @PersistenceContext
    private EntityManager entities;

    @Override
    public Customer add(Customer customer) {
        // persist(), never save(). CrudRepository.save() on an assigned @Id that already exists is
        // a MERGE, so a second registration would overwrite the first silently — same document
        // count, different owner, different password. That is the #25 account takeover reached by
        // another route, and it is why add() and update() below do not share an implementation.
        //
        // The legacy got this refusal from the CMP container: SignOnEJB.createUser was one line,
        // ulh.create(userName, password), and the container rejected a second create on the same
        // primary key. The check here is the same guarantee, named.
        if (this.entities.find(CustomerEntity.class, customer.userId()) != null) {
            throw new DuplicateAccountException(customer.userId());
        }

        CustomerEntity entity = CustomerEntity.from(customer);
        this.entities.persist(entity);
        return entity.toDomain();
    }

    @Override
    public Customer update(Customer customer) {
        return this.entities.merge(CustomerEntity.from(customer)).toDomain();
    }

    @Override
    public Optional<Customer> findByUserId(String userId) {
        return Optional.ofNullable(this.entities.find(CustomerEntity.class, userId)).map(CustomerEntity::toDomain);
    }

    @Override
    public List<Customer> findAll() {
        return this.entities.createQuery("select c from CustomerEntity c", CustomerEntity.class)
                .getResultList().stream().map(CustomerEntity::toDomain).toList();
    }

}
