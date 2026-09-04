package com.pedrolima.wexchange.adapter.in.web;

import com.pedrolima.wexchange.domain.error.ExchangeRateNotFoundException;
import com.pedrolima.wexchange.domain.error.IdempotencyKeyConflictException;
import com.pedrolima.wexchange.domain.error.MultipleCountryCurrenciesException;
import com.pedrolima.wexchange.domain.error.PayloadTooLargeException;
import com.pedrolima.wexchange.domain.error.PurchaseConversionException;
import com.pedrolima.wexchange.domain.error.RateLimitExceededException;
import com.pedrolima.wexchange.domain.error.ResourceNotFoundException;
import com.pedrolima.wexchange.domain.error.RetryableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/**
 * Maps every exception the API can produce to an RFC 9457
 * {@code application/problem+json} response.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} is what keeps this class
 * small: as of Spring Framework 6, its default handlers for the framework's own
 * exceptions (malformed JSON, missing parameters, unsupported method, unsupported
 * media type, bean-validation failures) already return {@link ProblemDetail}.
 * {@link #handleExceptionInternal} is the one hook all of those funnel through,
 * so enriching it once with {@code type}, {@code code}, {@code instance}, and
 * {@code traceId} covers them all; only the domain exceptions below need their
 * own handler.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final URI PROBLEM_TYPE_BASE = URI.create("https://wexchange.pedrolima.com/problems/");

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(final ResourceNotFoundException ex, final WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", ex.getMessage(), request);
    }

    @ExceptionHandler(ExchangeRateNotFoundException.class)
    public ProblemDetail handleExchangeRateNotFound(final ExchangeRateNotFoundException ex, final WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "exchange-rate-not-found", ex.getMessage(), request);
    }

    @ExceptionHandler(MultipleCountryCurrenciesException.class)
    public ProblemDetail handleAmbiguousCountryCurrency(
            final MultipleCountryCurrenciesException ex,
            final WebRequest request
    ) {
        return problem(HttpStatus.CONFLICT, "ambiguous-country-currency", ex.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ProblemDetail handleIdempotencyKeyConflict(
            final IdempotencyKeyConflictException ex,
            final WebRequest request
    ) {
        return problem(HttpStatus.CONFLICT, "idempotency-key-conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(RetryableException.class)
    public ProblemDetail handleUpstreamUnavailable(final RetryableException ex, final WebRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "upstream-unavailable", ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(
            final RateLimitExceededException ex,
            final WebRequest request
    ) {
        final var problemDetail = problem(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()))
                .body(problemDetail);
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ProblemDetail handlePayloadTooLarge(final PayloadTooLargeException ex, final WebRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "payload-too-large", ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(final IllegalArgumentException ex, final WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-argument", ex.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(final ConstraintViolationException ex, final WebRequest request) {
        final var problemDetail = problem(HttpStatus.BAD_REQUEST, "validation-failed",
                "The request parameters failed validation.", request);
        problemDetail.setProperty("violations", ex.getConstraintViolations().stream()
                .map(violation -> new Violation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList());
        return problemDetail;
    }

    @ExceptionHandler(PurchaseConversionException.class)
    public ProblemDetail handlePurchaseConversionFailure(
            final PurchaseConversionException ex,
            final WebRequest request
    ) {
        log.error("Purchase conversion failed", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "purchase-conversion-failed",
                "Unable to convert the purchase.", request);
    }

    /**
     * The sanitized fallback for anything not handled above. Never exposes the
     * exception's own message: an unclassified failure might carry a SQL error,
     * a stack frame, or another internal detail a caller has no business seeing.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(final Exception ex, final WebRequest request) {
        final var problemDetail = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "An unexpected error occurred. Include the trace identifier when contacting support.",
                request);
        log.error("Unhandled exception, traceId={}", problemDetail.getProperties().get("traceId"), ex);
        return problemDetail;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            final MethodArgumentNotValidException ex,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request
    ) {
        final var response = super.handleMethodArgumentNotValid(ex, headers, status, request);
        if (response.getBody() instanceof ProblemDetail problemDetail) {
            problemDetail.setProperty("violations", ex.getFieldErrors().stream()
                    .map(fieldError -> new Violation(fieldError.getField(), fieldError.getDefaultMessage()))
                    .toList());
            enrich(problemDetail, "validation-failed", request);
        }
        return response;
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            final MissingServletRequestParameterException ex,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request
    ) {
        return enrichSuperResponse(super.handleMissingServletRequestParameter(ex, headers, status, request), ex, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            final HttpMediaTypeNotSupportedException ex,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request
    ) {
        return enrichSuperResponse(super.handleHttpMediaTypeNotSupported(ex, headers, status, request), ex, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            final HttpRequestMethodNotSupportedException ex,
            final HttpHeaders headers,
            final HttpStatusCode status,
            final WebRequest request
    ) {
        return enrichSuperResponse(super.handleHttpRequestMethodNotSupported(ex, headers, status, request), ex, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            final Exception ex,
            final Object body,
            final HttpHeaders headers,
            final HttpStatusCode statusCode,
            final WebRequest request
    ) {
        if (body instanceof ProblemDetail problemDetail) {
            enrich(problemDetail, codeFor(ex), request);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    /**
     * Several of {@link ResponseEntityExceptionHandler}.s default handlers build
     * their {@link ProblemDetail} body without routing it back through
     * {@link #handleExceptionInternal}, so enriching only that one hook misses
     * them. Re-using whatever {@code super} already built - rather than
     * reconstructing the response - keeps behaviour such as the {@code Allow}
     * header on a 405 exactly as Spring computes it.
     */
    private static ResponseEntity<Object> enrichSuperResponse(
            final ResponseEntity<Object> response,
            final Exception ex,
            final WebRequest request
    ) {
        if (response.getBody() instanceof ProblemDetail problemDetail) {
            enrich(problemDetail, codeFor(ex), request);
        }
        return response;
    }

    private static ProblemDetail problem(
            final HttpStatus status,
            final String code,
            final String detail,
            final WebRequest request
    ) {
        final var problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setDetail(detail);
        return enrich(problemDetail, code, request);
    }

    private static ProblemDetail enrich(
            final ProblemDetail problemDetail,
            final String code,
            final WebRequest request
    ) {
        problemDetail.setType(PROBLEM_TYPE_BASE.resolve(code));
        problemDetail.setInstance(URI.create(requestPath(request)));
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("traceId", traceId(request));
        return problemDetail;
    }

    /**
     * Stable, caller-facing codes for the exceptions {@link ResponseEntityExceptionHandler}
     * maps to {@link ProblemDetail} on its own. Kept in one place so a new
     * framework exception falls back to a generic code rather than failing to
     * compile or silently omitting one.
     */
    private static String codeFor(final Exception ex) {
        if (ex instanceof HttpMessageNotReadableException readable) {
            return hasCause(readable, DeserializationException.class) ? "invalid-date-format" : "malformed-request-body";
        }
        if (ex instanceof MissingServletRequestParameterException) {
            return "missing-parameter";
        }
        if (ex instanceof TypeMismatchException) {
            return "type-mismatch";
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return "method-not-allowed";
        }
        // HttpMediaTypeNotSupportedException is the only type left that reaches
        // this method: every other @ExceptionHandler in this class builds its
        // own ProblemDetail directly instead of routing through here.
        return "unsupported-media-type";
    }

    private static boolean hasCause(final Throwable ex, final Class<? extends Throwable> causeType) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (causeType.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static String requestPath(final WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    /**
     * Falls back to a fresh identifier if {@link TraceIdFilter} did not run for
     * this request - a defence against filter mis-ordering, not against a
     * missing {@link ServletWebRequest}, which this Servlet-only application
     * always supplies.
     */
    private static String traceId(final WebRequest request) {
        final HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        final var attribute = servletRequest.getAttribute(TraceIdFilter.REQUEST_ATTRIBUTE);
        return attribute != null ? attribute.toString() : java.util.UUID.randomUUID().toString();
    }

    /** One field-level validation failure, reported under the {@code violations} extension member. */
    record Violation(String field, String message) {
    }
}
