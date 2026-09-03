package com.jucasoliveira.kitchensink.catalog.domain;

import java.util.Map;

public record Product(String id, String categoryId, Map<String, ProductDetails> details) {

}
