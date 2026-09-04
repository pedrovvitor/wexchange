package com.pedrolima.wexchange.domain.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(final String message) {
        super(message);
    }
}
