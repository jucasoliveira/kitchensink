package com.jucasoliveira.kitchensink.catalog.domain;

import java.util.List;

public record ItemDetails(String listPrice, String unitCost, List<String> attributes, String image,
                String description) {

}