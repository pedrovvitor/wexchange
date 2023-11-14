package com.pedrolima.wexchange.exception;

public class RetryableException extends RuntimeException {

    public RetryableException(final String message) {
        super(message);
    }
}
