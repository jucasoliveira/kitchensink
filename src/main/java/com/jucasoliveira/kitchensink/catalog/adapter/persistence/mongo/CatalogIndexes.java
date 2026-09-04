package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
@Profile("mongo")
@Order(200)
class CatalogIndexes implements ApplicationRunner {

    private final MongoTemplate mongo;

    CatalogIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) {
        this.mongo.indexOps(ProductDocument.class).createIndex(new Index().on("categoryId", Direction.ASC));
        this.mongo.indexOps(ItemDocument.class).createIndex(new Index().on("productId", Direction.ASC));
    }

}
