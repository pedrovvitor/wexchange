package com.pedrolima.wexchange.input;

import com.pedrolima.wexchange.purchase.models.CreatePurchaseApiInput;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Bean Validation on the create-purchase request body.
 *
 * <p>These tests assert which constraint fired, not the sentence it renders.
 * Violation messages come from Hibernate Validator's resource bundle and are
 * translated to the JVM's default locale, so asserting on their text made the
 * suite pass in English and fail in Portuguese. The constraint annotation is
 * the stable, locale-independent fact, and it is also the stronger assertion:
 * "the size rule rejected this" rather than "some rule produced this sentence".
 */
public class CreatePurchaseApiInputValidationTest {

    private static final LocalDate A_PURCHASE_DATE = LocalDate.of(2024, 1, 31);

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void givenInvalidBlankDescription_whenValidating_thenDescriptionConstraintViolations() {
        final var input = new CreatePurchaseApiInput("   ", A_PURCHASE_DATE, BigDecimal.valueOf(100.0));

        assertSingleViolation(validator.validate(input), "description", NotBlank.class);
    }

    @Test
    void givenInvalidShortDescription_whenValidating_thenDescriptionSizeConstraintViolations() {
        final var input = new CreatePurchaseApiInput("aa", A_PURCHASE_DATE, BigDecimal.valueOf(100.0));

        assertSingleViolation(validator.validate(input), "description", Size.class);
    }

    @Test
    void givenInvalidLongDescription_whenValidating_thenDescriptionSizeConstraintViolations() {
        final var input = new CreatePurchaseApiInput("a".repeat(51), A_PURCHASE_DATE, BigDecimal.valueOf(100.0));

        assertSingleViolation(validator.validate(input), "description", Size.class);
    }

    @Test
    @DisplayName("a description exactly at the size boundary is accepted")
    void givenDescriptionAtTheSizeBoundary_whenValidating_thenNoViolation() {
        assertEquals(0, validator.validate(
                new CreatePurchaseApiInput("abc", A_PURCHASE_DATE, BigDecimal.valueOf(100.0))).size());
        assertEquals(0, validator.validate(
                new CreatePurchaseApiInput("a".repeat(50), A_PURCHASE_DATE, BigDecimal.valueOf(100.0))).size());
    }

    @Test
    void givenInvalidNullAmount_whenValidating_thenAmountConstraintViolations() {
        final var input = new CreatePurchaseApiInput("Valid Description", A_PURCHASE_DATE, null);

        assertSingleViolation(validator.validate(input), "amount", NotNull.class);
    }

    @Test
    void givenInvalidNegativeAmount_whenValidating_thenAmountConstraintViolations() {
        final var input =
                new CreatePurchaseApiInput("Valid Description", A_PURCHASE_DATE, BigDecimal.valueOf(-1.0));

        assertSingleViolation(validator.validate(input), "amount", DecimalMin.class);
    }

    @Test
    @DisplayName("an amount of exactly zero is accepted")
    void givenZeroAmount_whenValidating_thenNoViolation() {
        final var input = new CreatePurchaseApiInput("Valid Description", A_PURCHASE_DATE, new BigDecimal("0.00"));

        assertEquals(0, validator.validate(input).size());
    }

    @Test
    void givenNullDate_whenValidating_thenDateConstraintViolations() {
        final var input = new CreatePurchaseApiInput("Valid Description", null, BigDecimal.valueOf(100.0));

        assertSingleViolation(validator.validate(input), "date", NotNull.class);
    }

    private static void assertSingleViolation(
            final Set<ConstraintViolation<CreatePurchaseApiInput>> violations,
            final String expectedProperty,
            final Class<? extends Annotation> expectedConstraint
    ) {
        assertFalse(violations.isEmpty());
        assertEquals(1, violations.size());

        final ConstraintViolation<CreatePurchaseApiInput> violation = violations.iterator().next();
        assertEquals(expectedProperty, violation.getPropertyPath().toString());
        assertEquals(expectedConstraint,
                violation.getConstraintDescriptor().getAnnotation().annotationType());
    }
}
