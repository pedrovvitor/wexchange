package com.pedrolima.wexchange.exception;

public record ErrorResponse(
        long timestamp,
        int status,
        String message,
        String path) {

}

