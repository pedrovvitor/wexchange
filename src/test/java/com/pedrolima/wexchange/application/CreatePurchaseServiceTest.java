package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.application.port.ExchangeRateRefresher;
import com.pedrolima.wexchange.application.port.IdempotencyRecord;
import com.pedrolima.wexchange.application.port.IdempotencyStatus;
import com.pedrolima.wexchange.application.port.PurchaseIdempotencyStore;
import com.pedrolima.wexchange.application.port.PurchaseStore;
import com.pedrolima.wexchange.domain.error.IdempotencyKeyConflictException;
import com.pedrolima.wexchange.domain.error.RetryableException;
import com.pedrolima.wexchange.domain.money.Money;
import com.pedrolima.wexchange.domain.purchase.Purchase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The use case owns two decisions that used to be ambient: which identifier a
 * purchase gets and what "now" means. Both are injected, so these tests assert
 * the exact values reaching storage instead of asserting around a random UUID
 * and a wall clock. No Spring context is involved: the service is a plain class.
 *
 * <p>{@code idempotencyKey} tests (issue #18) use a real, very short
 * {@code pollInterval}/{@code maxWait} so the "still in progress" loop in
 * {@code CreatePurchaseService} runs to completion in milliseconds rather than
 * seconds.
 */
@ExtendWith(MockitoExtension.class)
class CreatePurchaseServiceTest {

    private static final String ID = "2f8c6d10-9a4b-4e73-85cf-13d0b7e6a924";

    private static final Instant NOW = Instant.parse("2024-01-31T12:00:00Z");

    private static final LocalDate DATE = LocalDate.of(2024, 1, 31);

    private static final String KEY = "a-client-generated-key";

    @Mock
    private PurchaseStore purchases;

    @Mock
    private ExchangeRateRefresher rateRefresher;

    @Mock
    private PurchaseIdempotencyStore idempotencyStore;

    private CreatePurchaseService service;

    @BeforeEach
    void setUp() {
        service = new CreatePurchaseService(
                purchases, rateRefresher, () -> ID, Clock.fixed(NOW, ZoneOffset.UTC),
                idempotencyStore, Duration.ofHours(24), Duration.ofMillis(200), Duration.ofMillis(5));
    }

    private static IdempotencyRecord recordOf(final IdempotencyStatus status, final String resourceId) {
        return new IdempotencyRecord(KEY, fingerprintOf("Test Purchase", DATE, "100.00"), status, resourceId, NOW.plusSeconds(60));
    }

    /** Mirrors {@code CreatePurchaseService}'s own canonical fingerprint, to build matching test fixtures. */
    private static String fingerprintOf(final String description, final LocalDate date, final String amount) {
        final String canonicalPayload = description.length() + ":" + description
                + "|" + date
                + "|" + Money.of(amount);
        try {
            final var digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonicalPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (final java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // -- no idempotency key: exactly the pre-existing behaviour ------------

    @Test
    @DisplayName("the stored purchase carries the generated identifier and the clock's instant")
    void givenValidInput_whenCreating_thenIdentityAndTimestampsComeFromTheCollaborators() {
        when(purchases.save(any(Purchase.class))).thenAnswer(call -> call.getArgument(0));
        when(purchases.countByPurchaseDate(DATE)).thenReturn(2L);

        service.execute("Test Purchase", DATE, new BigDecimal("100.0"), null);

        final var saved = ArgumentCaptor.forClass(Purchase.class);
        verify(purchases).save(saved.capture());

        assertEquals(ID, saved.getValue().id());
        assertEquals(NOW, saved.getValue().createdAt());
        assertEquals(NOW, saved.getValue().updatedAt());
        assertEquals(Money.of("100.00"), saved.getValue().amount());
    }

    @Test
    @DisplayName("the first purchase of a date warms the rate cache for its window")
    void givenFirstPurchaseOfTheDate_whenCreating_thenRatesAreRefreshed() {
        final var stored = Purchase.create(ID, "Test Purchase", DATE, Money.of("100.00"), NOW);
        when(purchases.save(any(Purchase.class))).thenReturn(stored);
        when(purchases.countByPurchaseDate(DATE)).thenReturn(1L);

        service.execute("Test Purchase", DATE, new BigDecimal("100.00"), null);

        verify(rateRefresher).refreshFor(stored);
    }

    @Test
    @DisplayName("a later purchase on a covered date does not refresh again")
    void givenLaterPurchaseOfTheDate_whenCreating_thenRatesAreNotRefreshed() {
        when(purchases.save(any(Purchase.class))).thenAnswer(call -> call.getArgument(0));
        when(purchases.countByPurchaseDate(DATE)).thenReturn(2L);

        service.execute("Test Purchase", DATE, new BigDecimal("100.00"), null);

        verify(rateRefresher, never()).refreshFor(any());
    }

    @Test
    @DisplayName("what storage returns is what the caller gets, not the instance passed in")
    void givenStorageAssignsValues_whenCreating_thenTheStoredPurchaseIsReturned() {
        final var stored = Purchase.create(ID, "As stored", DATE, Money.of("100.00"), NOW);
        when(purchases.save(any(Purchase.class))).thenReturn(stored);
        when(purchases.countByPurchaseDate(DATE)).thenReturn(2L);

        assertEquals(stored, service.execute("Test Purchase", DATE, new BigDecimal("100.00"), null));
    }

    @Test
    @DisplayName("without a key, two calls with the same payload each create their own purchase")
    void givenNoIdempotencyKey_whenCreatingTwice_thenNeitherCallTouchesTheIdempotencyStore() {
        when(purchases.save(any(Purchase.class))).thenAnswer(call -> call.getArgument(0));
        when(purchases.countByPurchaseDate(DATE)).thenReturn(1L);

        service.execute("Test Purchase", DATE, new BigDecimal("100.00"), null);
        service.execute("Test Purchase", DATE, new BigDecimal("100.00"), null);

        verify(purchases, times(2)).save(any());
        org.mockito.Mockito.verifyNoInteractions(idempotencyStore);
    }

    // -- a new idempotency key: this call becomes the owner -----------------

    @Test
    @DisplayName("a new idempotency key is claimed, the purchase is created, and the claim is completed with its id")
    void givenANewIdempotencyKey_whenCreating_thenTheClaimIsCompletedWithThePurchaseId() {
        when(idempotencyStore.claim(eq(KEY), anyString(), any(), any())).thenReturn(true);
        when(purchases.save(any(Purchase.class))).thenAnswer(call -> call.getArgument(0));
        when(purchases.countByPurchaseDate(DATE)).thenReturn(1L);

        final var result = service.execute("Test Purchase", DATE, new BigDecimal("100.00"), KEY);

        assertEquals(ID, result.id());
        verify(purchases).save(any());
        verify(rateRefresher).refreshFor(any());
        verify(idempotencyStore).markCompleted(KEY, ID, NOW);
        verify(idempotencyStore, never()).markFailed(anyString(), any());
    }

    @Test
    @DisplayName("when creation fails after a claim is won, the claim is marked failed and the failure propagates")
    void givenTheClaimIsWon_whenCreationFails_thenTheClaimIsMarkedFailedAndTheExceptionPropagates() {
        when(idempotencyStore.claim(eq(KEY), anyString(), any(), any())).thenReturn(true);
        final var failure = new RuntimeException("save failed");
        when(purchases.save(any(Purchase.class))).thenThrow(failure);

        final var thrown = assertThrows(
                RuntimeException.class, () -> service.execute("Test Purchase", DATE, new BigDecimal("100.00"), KEY));

        assertEquals(failure, thrown);
        verify(idempotencyStore).markFailed(KEY, NOW);
        verify(idempotencyStore, never()).markCompleted(anyString(), anyString(), any());
        verify(rateRefresher, never()).refreshFor(any());
    }

    // -- an existing key: this call lost the claim, joins the existing one --

    @Test
    @DisplayName("a completed record with a matching fingerprint replays the original purchase without saving again")
    void givenACompletedRecordWithTheSameFingerprint_whenCreatingAgain_thenTheOriginalPurchaseIsReplayed() {
        final var original = Purchase.create(ID, "Test Purchase", DATE, Money.of("100.00"), NOW);
        when(idempotencyStore.claim(eq(KEY), anyString(), any(), any())).thenReturn(false);
        when(idempotencyStore.find(KEY)).thenReturn(Optional.of(recordOf(IdempotencyStatus.COMPLETED, ID)));
        when(purchases.findById(ID)).thenReturn(Optional.of(original));

        final var result = service.execute("Test Purchase", DATE, new BigDecimal("100.00"), KEY);

        assertEquals(original, result);
        verify(purchases, never()).save(any());
        verify(rateRefresher, never()).refreshFor(any());
    }

    @Test
    @DisplayName("reusing a key with a different payload is a conflict, not a replay or a new purchase")
    void givenAnExistingKeyWithADifferentFingerprint_whenCreating_thenAConflictIsThrown() {
        when(idempotencyStore.claim(eq(KEY), anyString(), any(), any())).thenReturn(false);
        when(idempotencyStore.find(KEY)).thenReturn(Optional.of(
                new IdempotencyRecord(KEY, "a-completely-different-fingerprint", IdempotencyStatus.COMPLETED, ID, NOW.plusSeconds(60))));

        final var thrown = assertThrows(IdempotencyKeyConflictException.class,
                () -> service.execute("Test Purchase", DATE, new BigDecimal("100.00"), KEY));

        assertEquals("Idempotency-Key " + KEY + " was already used with a different request", thrown.getMessage());
        verify(purchases, never()).save(any());
    }

    @Test
    @DisplayName("a failed record with a matching fingerprint is reclaimed and retried as the new owner")
    void givenAFailedRecord_whenCreating_thenItIsReclaimedAndRetried() {
        when(idempotencyStore.claim(eq(KEY), anyString(), any(), any())).thenReturn(false);
        when(idempotencyStore.find(KEY)).thenReturn(Optional.of(recordOf(IdempotencyStatus.FAILED, null)));
        when(idempotencyStore.reclaim(eq(KEY), any())).thenReturn(true);
        when(purchases.save(any(Purchase.class))).thenAnswer(call -> call.getArgument(0));
        when(purchases.countByPurchaseDate(DATE)).thenReturn(1L);

        final var result = service.execute("Test Purchase", DATE, new BigDecimal("100.00"), KEY);

        assertEquals(ID, result.id());
        verify(idempotencyStore).markCompleted(KEY, ID, NOW);
    }

    @Test
    @DisplayName("when a failed record is reclaimed by someone else first, this call keeps polling instead of double-creating")
    void givenAFailedRecordReclaimedBySomeoneElse_whenCreating_thenItContinuesPollingRatherThanDoubleCreating() {
        when(idempotencyStore.claim(eq(KEY), anyString(), any(), any())).thenReturn(false);
        when(idempotencyStore.find(KEY))
                .thenReturn(Optional.of(recordOf(IdempotencyStatus.FAILED, null)))
                .thenReturn(Optional.of(recordOf(IdempotencyStatus.COMPLETED, ID)));
        when(idempotencyStore.reclaim(eq(KEY), any())).thenReturn(false);
        when(purchases.findById(ID)).thenReturn(Optional.of(Purchase.create(ID, "Test Purchase", DATE, Money.of("100.00"), NOW)));

        final var result = service.execute("Test Purchase", DATE, new BigDecimal("100.00"), KEY);

        assertEquals(ID, result.id());
        verify(purchases, never()).save(any());
    }

    @Test
    @DisplayName("an in-progress record still within its wait budget is polled until it resolves")
    void givenAnInProgressRecord_whenCreating_thenItPollsUntilCompletionAndReplays() {
        final var original = Purchase.create(ID, "Test Purchase", DATE, Money.of("100.00"), NOW);
        when(idempotencyStore.claim(eq(KEY), anyString(), any(), any())).thenReturn(false);
        when(idempotencyStore.find(KEY))
                .thenReturn(Optional.of(recordOf(IdempotencyStatus.IN_PROGRESS, null)))
                .thenReturn(Optional.of(recordOf(IdempotencyStatus.IN_PROGRESS, null)))
                .thenReturn(Optional.of(recordOf(IdempotencyStatus.COMPLETED, ID)));
        when(purchases.findById(ID)).thenReturn(Optional.of(original));

        final var result = service.execute("Test Purchase", DATE, new BigDecimal("100.00"), KEY);

        assertEquals(original, result);
        verify(purchases, never()).save(any());
    }

    @Test
    @DisplayName("an in-progress record that never resolves within the wait budget is reported as retryable, not not-found")
    void givenAnInProgressRecordThatNeverResolves_whenCreating_thenARetryableExceptionIsThrown() {
        // A real, advancing clock: the wait-budget loop's own deadline check needs
        // time to actually pass, which a Clock.fixed instance never does.
        service = new CreatePurchaseService(
                purchases, rateRefresher, () -> ID, Clock.systemUTC(),
                idempotencyStore, Duration.ofHours(24), Duration.ofMillis(20), Duration.ofMillis(5));
        when(idempotencyStore.claim(eq(KEY), anyString(), any(), any())).thenReturn(false);
        final var record = new IdempotencyRecord(
                KEY, fingerprintOf("Test Purchase", DATE, "100.00"), IdempotencyStatus.IN_PROGRESS, null, Instant.now().plusSeconds(60));
        when(idempotencyStore.find(KEY)).thenReturn(Optional.of(record));

        assertThrows(RetryableException.class,
                () -> service.execute("Test Purchase", DATE, new BigDecimal("100.00"), KEY));

        verify(purchases, never()).save(any());
    }

    @Test
    @DisplayName("a record that disappears between the lost claim and the lookup is reclaimed by this call")
    void givenTheRecordDisappearsBeforeTheLookup_whenCreating_thenThisCallClaimsItInstead() {
        when(idempotencyStore.claim(eq(KEY), anyString(), any(), any()))
                .thenReturn(false)
                .thenReturn(true);
        when(idempotencyStore.find(KEY)).thenReturn(Optional.empty());
        when(purchases.save(any(Purchase.class))).thenAnswer(call -> call.getArgument(0));
        when(purchases.countByPurchaseDate(DATE)).thenReturn(1L);

        final var result = service.execute("Test Purchase", DATE, new BigDecimal("100.00"), KEY);

        assertEquals(ID, result.id());
        verify(idempotencyStore).markCompleted(KEY, ID, NOW);
    }
}
