package com.pedrolima.wexchange.exceptions;

public class ExchangeRateNotFoundException extends RuntimeException {

    public ExchangeRateNotFoundException(String message) {
        super(message);
    }
}
