package com.pedrolima.wexchange.api;

import java.util.Map;

public record ApiLink(String rel, String href, String method, Map<String, String> params) {

    public static ApiLink with(String aRel, String aHref, String aMethod, Map<String, String> aParamsMap) {
        return new ApiLink(aRel, aHref, aMethod, aParamsMap);
    }
}

