package com.pedrolima.wexchange.exceptions;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(final String message) {
        super(message);
    }
}
