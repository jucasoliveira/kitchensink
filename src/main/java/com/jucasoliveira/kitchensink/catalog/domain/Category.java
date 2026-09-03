package com.jucasoliveira.kitchensink.catalog.domain;

import java.util.Map;

public record Category(String id, Map<String, CategoryDetails> details) {

}
