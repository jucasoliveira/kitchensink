package com.jucasoliveira.kitchensink.catalog.adapter.web;

import java.util.List;
import java.util.function.Function;

import com.jucasoliveira.kitchensink.catalog.application.CatalogPage;

public record PageView<T>(List<T> contents, int start, int size, boolean hasNext,
        boolean hasPrevious, int nextStart, int previousStart) {

    static <S, T> PageView<T> of(CatalogPage<S> page, Function<S, T> view) {
        return new PageView<>(page.contents().stream().map(view).toList(), page.start(), page.size(),
                page.hasNext(), page.previousPageAvailable(), page.startOfNextPage(), page.startOfPreviousPage());
    }
}