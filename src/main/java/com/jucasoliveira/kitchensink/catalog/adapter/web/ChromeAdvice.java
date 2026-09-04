package com.jucasoliveira.kitchensink.catalog.adapter.web;

import java.util.List;
import java.util.Locale;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.jucasoliveira.kitchensink.catalog.application.CatalogService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The layout chrome's own model data, on every screen that renders {@code layout.html}.
 *
 * <p>Legacy anchor: {@code sidebar.jsp:52-58} did its own {@code CatalogHelper} lookup with
 * {@code count=5, start=0} rather than depending on whichever action served the screen, and every
 * {@code <screen>} in {@code screendefinitions_en_US.xml} bound it. It lives in the catalog slice
 * for the same reason it did then: the sidebar is a catalog fragment the other screens embed.
 */
@ControllerAdvice
public class ChromeAdvice {

    /** sidebar.jsp:57-58 — the sidebar's own count, independent of the screen's paging. */
    private static final int SIDEBAR_COUNT = 5;

    private final CatalogService catalog;

    ChromeAdvice(CatalogService catalog) {
        this.catalog = catalog;
    }

    @ModelAttribute("categories")
    List<CategoryView> categories(Locale locale) {
        String key = CatalogController.localeKey(locale);
        return this.catalog.browseCategories(key, 0, SIDEBAR_COUNT).contents()
                .stream().map(category -> CategoryView.of(category, key)).toList();
    }

    /** banner.jsp:79-80 encodeRequestParameters — the flags returned you to the screen you were on. */
    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
