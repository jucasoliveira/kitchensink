package com.jucasoliveira.kitchensink.catalog.domain;

import java.util.Map;

public record Item(String id, String productId, Map<String, ItemDetails> details) {

}
