package com.pedrolima.wexchange.exceptions;

public record ErrorResponse(
        long timestamp,
        int status,
        String message,
        String path) {

}

