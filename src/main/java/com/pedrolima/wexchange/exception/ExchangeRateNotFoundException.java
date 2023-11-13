package com.pedrolima.wexchange.exception;

public class ExchangeRateNotFoundException extends RuntimeException {

    public static final String ERROR_MESSAGE = "Purchase cannot be converted to the target currency.";
    public ExchangeRateNotFoundException() {
        super(ERROR_MESSAGE);
    }
}
