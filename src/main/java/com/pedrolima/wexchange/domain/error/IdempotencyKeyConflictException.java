package com.pedrolima.wexchange.domain.error;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(final String message) {
        super(message);
    }
}
