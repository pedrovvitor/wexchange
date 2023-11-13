package com.pedrolima.wexchange.util;

import org.springframework.http.HttpMethod;

import java.util.Map;

public record RequestDetails(
        String urlRequest,
        String method,
        Map<String, String> header,
        String data
) {

    public static RequestDetails get(final String urlRequest, final Map<String, String> header, final String data) {
        return new RequestDetails(urlRequest, HttpMethod.GET.name(), header, data);
    }
}
