package com.jucasoliveira.kitchensink.catalog.application;

import java.util.List;

public record CatalogPage<T>(List<T> contents, int start, boolean hasNext) {
    public static <T> CatalogPage<T> of(List<T> all, int start, int count) {
        if (start < 0 || start >= all.size()) {
            return new CatalogPage<>(List.of(), 0, false);
        }
        int end = Math.min(start + Math.max(count, 1), all.size());
        return new CatalogPage<>(List.copyOf(all.subList(start, end)), start, end < all.size());
    }

    public boolean previousPageAvailable() {
        return this.start > 0;
    }

    public int startOfNextPage() {
        return this.start + this.contents.size();
    }

    public int startOfPreviousPage() {
        return Math.max(this.start - this.contents.size(), 0);
    }

    public int size() {
        return this.contents.size();
    }

}
