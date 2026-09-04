package com.pedrolima.wexchange.domain.error;

public class MultipleCountryCurrenciesException extends RuntimeException {

    public MultipleCountryCurrenciesException(final String message) {
        super(message);
    }
}
