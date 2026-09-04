package com.pedrolima.wexchange.adapter.out.fiscal;

import java.net.URI;
import java.net.http.HttpRequest;

public final class HttpRequestUtils {

    private HttpRequestUtils() {}

    public static HttpRequest buildHttpRequest(final String url) {
        return HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    }
}
