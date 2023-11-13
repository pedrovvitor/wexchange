package com.pedrolima.wexchange.exception;

public class PurchaseConversionException extends RuntimeException {

    public static final String ERROR_MESSAGE = "An error occurred while processing the conversion";
    public PurchaseConversionException() {
        super(ERROR_MESSAGE);
    }
}
