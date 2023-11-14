package com.pedrolima.wexchange.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.WebRequest;

import java.util.Optional;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({DeserializationException.class, HttpMessageNotReadableException.class})
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleDateTimeParseException(
            final RuntimeException ex,
            final WebRequest request
    ) {
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
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            final ResourceNotFoundException ex,
            final WebRequest request
    ) {
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
    public ResponseEntity<ErrorResponse> handleExchangeRateNotFoundException(
            final ExchangeRateNotFoundException ex,
            final WebRequest request
    ) {
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
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        ErrorResponse errorResponse = new ErrorResponse(
                System.currentTimeMillis(),
                status.value(),
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity
                .status(status)
                .body(errorResponse);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            final MissingServletRequestParameterException ex,
            final WebRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        ErrorResponse errorResponse = new ErrorResponse(
                System.currentTimeMillis(),
                status.value(),
                ex.getMessage(),
                request.getDescription(false)
        );

        return ResponseEntity
                .status(status)
                .body(errorResponse);
    }

    @ExceptionHandler({RetryableException.class})
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleCommunicationExceptions(
            final WebRequest request) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;

        ErrorResponse errorResponse = new ErrorResponse(
                System.currentTimeMillis(),
                status.value(),
                "Error communicating with external service",
                request.getDescription(false)
        );

        return ResponseEntity
                .status(status)
                .body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            final MethodArgumentNotValidException ex,
            final WebRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        final var errors = ex.getFieldErrors().stream()
                .map(fieldError -> {
                    String defaultMessage = Optional
                            .ofNullable(fieldError.getDefaultMessage())
                            .orElse("Invalid value");
                    return fieldError.getField() + " " + defaultMessage;
                })
                .collect(Collectors.joining(", "));

        ErrorResponse errorResponse = new ErrorResponse(
                System.currentTimeMillis(),
                ex.getStatusCode().value(),
                errors,
                request.getDescription(false)
        );

        return ResponseEntity
                .status(status)
                .body(errorResponse);
    }
}
