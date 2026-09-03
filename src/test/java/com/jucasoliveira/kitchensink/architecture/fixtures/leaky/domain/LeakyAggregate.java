package com.jucasoliveira.kitchensink.architecture.fixtures.leaky.domain;

import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Violates {@code the_domain_is_plain_java} (and {@code mongodb_types_stay_in_the_mongo_adapter}):
 * a Spring Data annotation on an aggregate. ADR-0005 §1 forbids exactly this, because an aggregate
 * that carries the Mongo mapping cannot also carry the JPA one.
 */
@Document("leaky")
public record LeakyAggregate(String id) {
}
