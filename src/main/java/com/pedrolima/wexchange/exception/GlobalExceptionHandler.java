package com.pedrolima.wexchange.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import java.time.format.DateTimeParseException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DateTimeParseException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleDateTimeParseException(
            final DateTimeParseException ex,
            final WebRequest request
    ) {
        log.debug("Handling HttpMessageNotReadableException: ", ex);
        HttpStatus status = HttpStatus.BAD_REQUEST;

        ErrorResponse errorResponse = new ErrorResponse(
                System.currentTimeMillis(),
                status.value(),
                ex.getLocalizedMessage(),
                request.getDescription(false)
        );

        return ResponseEntity
                .status(status)
                .body(errorResponse);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleDateTimeParseException(
            final ResourceNotFoundException ex,
            final WebRequest request
    ) {
        log.debug("Handling ResourceNotFoundException: ", ex);
        HttpStatus status = HttpStatus.NOT_FOUND;

        ErrorResponse errorResponse = new ErrorResponse(
                System.currentTimeMillis(),
                status.value(),
                ex.getLocalizedMessage(),
                request.getDescription(false)
        );

        return ResponseEntity
                .status(status)
                .body(errorResponse);
    }
    @ExceptionHandler(ExchangeRateNotFoundException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleDateTimeParseException(
            final ExchangeRateNotFoundException ex,
            final WebRequest request
    ) {
        log.debug("Handling ExchangeRateNotFoundException: ", ex);
        HttpStatus status = HttpStatus.NOT_FOUND;

        ErrorResponse errorResponse = new ErrorResponse(
                System.currentTimeMillis(),
                status.value(),
                ex.getLocalizedMessage(),
                request.getDescription(false)
        );

        return ResponseEntity
                .status(status)
                .body(errorResponse);
    }

    @ExceptionHandler(PurchaseConversionException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handlePurchaseConversionException(
            final PurchaseConversionException ex,
            final WebRequest request
    ) {
        log.error("Handling PurchaseConversionException", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        ErrorResponse errorResponse = new ErrorResponse(
                System.currentTimeMillis(),
                status.value(),
                ex.getMessage(), // Generic error message
                request.getDescription(false)
        );

        return ResponseEntity
                .status(status)
                .body(errorResponse);
    }
}
