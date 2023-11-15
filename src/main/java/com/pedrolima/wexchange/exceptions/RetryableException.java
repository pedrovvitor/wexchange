package com.pedrolima.wexchange.exceptions;

public class RetryableException extends RuntimeException {

    public RetryableException(final String message, final Exception e) {
        super(message, e);
    }

    public RetryableException(final String message) {
        super(message);
    }
}
