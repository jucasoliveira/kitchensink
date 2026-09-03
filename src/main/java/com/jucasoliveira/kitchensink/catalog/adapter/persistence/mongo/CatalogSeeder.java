package com.jucasoliveira.kitchensink.catalog.adapter.persistence.mongo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("mongo")
@ConditionalOnProperty(name = "kitchensink.seed.catalog", havingValue = "true")
public class CatalogSeeder implements ApplicationRunner {
    private static final String FIXTURE = "seed/catalog.json";
    private static final List<String> COLLECTIONS = List.of("categories", "products", "items");

    private final MongoTemplate mongo;

    CatalogSeeder(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        String json = new ClassPathResource(FIXTURE).getContentAsString(StandardCharsets.UTF_8);
        Document fixture = Document.parse(json);
        for (String collection : COLLECTIONS) {
            mongo.dropCollection(collection);
            mongo.insert(fixture.getList(collection, Document.class), collection);
        }
    }

}
