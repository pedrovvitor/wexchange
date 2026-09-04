package com.pedrolima.wexchange.adapter.in.web.ratelimit;

import org.springframework.util.AntPathMatcher;

/** The abuse-control-relevant routes (issue #17): every other path gets only the global limit. */
public enum Route {

    PURCHASE_CREATION,
    CONVERSION,
    COUNTRY_CURRENCIES,
    OTHER;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    public static Route classify(final String method, final String path) {
        if ("POST".equalsIgnoreCase(method) && PATH_MATCHER.match("/v1/purchases", path)) {
            return PURCHASE_CREATION;
        }
        if ("GET".equalsIgnoreCase(method) && PATH_MATCHER.match("/v1/purchases/*/convert", path)) {
            return CONVERSION;
        }
        if ("GET".equalsIgnoreCase(method) && PATH_MATCHER.match("/v1/country_currencies", path)) {
            return COUNTRY_CURRENCIES;
        }
        return OTHER;
    }
}
