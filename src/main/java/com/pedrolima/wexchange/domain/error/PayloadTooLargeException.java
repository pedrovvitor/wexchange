package com.pedrolima.wexchange.domain.error;

public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(final String message) {
        super(message);
    }
}
