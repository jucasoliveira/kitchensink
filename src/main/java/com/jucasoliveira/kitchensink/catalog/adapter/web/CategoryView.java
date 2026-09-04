package com.jucasoliveira.kitchensink.catalog.adapter.web;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import com.jucasoliveira.kitchensink.catalog.domain.Category;
import com.jucasoliveira.kitchensink.catalog.domain.CategoryDetails;
import com.jucasoliveira.kitchensink.catalog.domain.Item;
import com.jucasoliveira.kitchensink.catalog.domain.ItemDetails;
import com.jucasoliveira.kitchensink.catalog.domain.Product;
import com.jucasoliveira.kitchensink.catalog.domain.ProductDetails;

public record CategoryView(String id, String name, String image, String description) {
    public static CategoryView of(Category category, String locale) {
        CategoryDetails details = category.details().get(locale);
        return new CategoryView(category.id(), details.name(), details.image(), details.description());
    }
}

record ProductView(String id, String name, String image, String description) {
    static ProductView of(Product product, String locale) {
        ProductDetails details = product.details().get(locale);
        return new ProductView(product.id(), details.name(), details.image(), details.description());
    }
}

record ItemView(String id, String productId, String productName, String attributes,
        String listPrice, String unitCost, String image, String description) {

    static ItemView of(Item item, Product product, String locale) {
        ItemDetails details = item.details().get(locale);
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag(locale.replace('_', '-')));
        return new ItemView(item.id(), product.id(), product.details().get(locale).name(),
                String.join(" ", details.attributes()),
                currency.format(new BigDecimal(details.listPrice())),
                currency.format(new BigDecimal(details.unitCost())),
                details.image(), details.description());
    }
}
