package com.pedrolima.wexchange.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class HttpRequestUtils {

    private HttpRequestUtils() {

    }

    public static HttpRequest buildHttpRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
    }

    public static HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
