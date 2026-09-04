package com.pedrolima.wexchange.adapter.in.web;

public record ErrorResponse(
        long timestamp,
        int status,
        String message,
        String path) {

}
