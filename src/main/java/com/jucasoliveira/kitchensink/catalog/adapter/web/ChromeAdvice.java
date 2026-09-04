package com.jucasoliveira.kitchensink.catalog.adapter.web;

import java.security.Principal;
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

    /**
     * {@code banner.jsp:65-68} — the banner chose between "Sign out" and "Sign in" on the
     * {@code ${j_signon}} session flag that {@code SignOnFilter} maintained by hand.
     *
     * <p>{@code Principal} rather than {@code Authentication}: with anonymous authentication
     * enabled an anonymous request still carries an {@code AnonymousAuthenticationToken} whose
     * {@code isAuthenticated()} returns <em>true</em>, so the obvious check would show "Sign out"
     * to everyone. {@code HttpServletRequest.getUserPrincipal()} is null for anonymous, which is
     * the distinction the banner actually needs — the same mechanism {@code CustomerController}
     * uses to decide whether to populate the member table.
     *
     * <p>Not {@code sec:authorize}: that needs {@code thymeleaf-extras-springsecurity}, and a
     * dependency is a poor trade for one null check (AGENTS.md §2).
     */
    @ModelAttribute("signedOnAs")
    String signedOnAs(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    /** banner.jsp:79-80 encodeRequestParameters — the flags returned you to the screen you were on. */
    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
