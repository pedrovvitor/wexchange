package com.pedrolima.wexchange.exceptions;

public class ExchangeRateNotFoundException extends RuntimeException {

    public ExchangeRateNotFoundException(final String message) {
        super(message);
    }
}
