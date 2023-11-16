package com.pedrolima.wexchange.api;

import java.util.Map;

public record ApiLink(String rel, String href, String method, Map<String, String> params) {

    public static ApiLink with(
            final String aRel,
            final String aHref,
            final String aMethod,
            final Map<String, String> aParamsMap
    ) {
        return new ApiLink(aRel, aHref, aMethod, aParamsMap);
    }
}

