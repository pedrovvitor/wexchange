package com.pedrolima.wexchange.input;

import com.pedrolima.wexchange.api.purchase.CreatePurchaseApiInput;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CreatePurchaseApiInputValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void givenInvalidBlankDescription_whenValidating_thenDescriptionConstraintViolations() {
        final var input = new CreatePurchaseApiInput("   ", LocalDate.now(), BigDecimal.valueOf(100.0));
        Set<ConstraintViolation<CreatePurchaseApiInput>> violations = validator.validate(input);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        ConstraintViolation<CreatePurchaseApiInput> violation = violations.iterator().next();
        assertEquals("description", violation.getPropertyPath().toString());
        assertEquals("must not be blank", violation.getMessage());
    }

    @Test
    void givenInvalidShortDescription_whenValidating_thenDescriptionSizeConstraintViolations() {
        final var shortDescription = "aa";
        var input = new CreatePurchaseApiInput(shortDescription, LocalDate.now(), BigDecimal.valueOf(100.0));
        Set<ConstraintViolation<CreatePurchaseApiInput>> violations = validator.validate(input);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        ConstraintViolation<CreatePurchaseApiInput> violation = violations.iterator().next();
        assertEquals("description", violation.getPropertyPath().toString());
        assertEquals("size must be between 3 and 50", violation.getMessage());
    }

    @Test
    void givenInvalidLongDescription_whenValidating_thenDescriptionSizeConstraintViolations() {
        final var longDescription = "a".repeat(51);
        var input = new CreatePurchaseApiInput(longDescription, LocalDate.now(), BigDecimal.valueOf(100.0));
        Set<ConstraintViolation<CreatePurchaseApiInput>> violations = validator.validate(input);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        ConstraintViolation<CreatePurchaseApiInput> violation = violations.iterator().next();
        assertEquals("description", violation.getPropertyPath().toString());
        assertEquals("size must be between 3 and 50", violation.getMessage());
    }

    @Test
    void givenInvalidNullAmount_whenValidating_thenAmountConstraintViolations() {
        final var input = new CreatePurchaseApiInput("Valid Description", LocalDate.now(), null); // Negative amount
        Set<ConstraintViolation<CreatePurchaseApiInput>> violations = validator.validate(input);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        ConstraintViolation<CreatePurchaseApiInput> violation = violations.iterator().next();
        assertEquals("amount", violation.getPropertyPath().toString());
        assertEquals("must not be null", violation.getMessage());
    }

    @Test
    void givenInvalidNegativeAmount_whenValidating_thenAmountConstraintViolations() {
        final var input = new CreatePurchaseApiInput("Valid Description", LocalDate.now(), BigDecimal.valueOf(-1.0)); // Negative amount
        Set<ConstraintViolation<CreatePurchaseApiInput>> violations = validator.validate(input);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        ConstraintViolation<CreatePurchaseApiInput> violation = violations.iterator().next();
        assertEquals("amount", violation.getPropertyPath().toString());
        assertEquals("must be greater than or equal to 0.00", violation.getMessage());
    }

    @Test
    void givenNullDate_whenValidating_thenDateConstraintViolations() {
        final var input = new CreatePurchaseApiInput("Valid Description", null, BigDecimal.valueOf(100.0));
        Set<ConstraintViolation<CreatePurchaseApiInput>> violations = validator.validate(input);

        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());
        ConstraintViolation<CreatePurchaseApiInput> violation = violations.iterator().next();
        assertEquals("date", violation.getPropertyPath().toString());
        assertEquals("must not be null", violation.getMessage());
    }
}
