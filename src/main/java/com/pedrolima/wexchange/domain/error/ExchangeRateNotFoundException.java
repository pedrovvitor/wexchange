package com.pedrolima.wexchange.domain.error;

public class ExchangeRateNotFoundException extends RuntimeException {

    public ExchangeRateNotFoundException(final String message) {
        super(message);
    }
}
