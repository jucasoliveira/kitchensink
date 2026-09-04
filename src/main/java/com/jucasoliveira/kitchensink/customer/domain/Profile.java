package com.jucasoliveira.kitchensink.customer.domain;

public record Profile(String preferredLanguage, String favoriteCategory,
        boolean myListPreference, boolean bannerPreference) {

    public static final Profile DEFAULT = new Profile("en_US", null, true, true);
}