package com.jucasoliveira.kitchensink;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MemberRepository extends MongoRepository<Member, String> {
    boolean existsByEmail(String email);
}
