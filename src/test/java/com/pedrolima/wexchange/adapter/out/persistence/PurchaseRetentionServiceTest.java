package com.pedrolima.wexchange.adapter.out.persistence;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The cutoff computation is the one thing worth asserting precisely here:
 * {@link PurchaseRepository#deleteByCreatedAtBefore} is already a trusted
 * primitive, so the service's own job is getting "now minus retention" right
 * and passing the deleted count through to the metric unchanged.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2024-07-15T12:00:00Z");

    private static final String DELETED_METRIC = "wexchange.application.purchase.retention.deleted.count";

    @Mock
    private PurchaseRepository repository;

    private SimpleMeterRegistry meterRegistry;

    private PurchaseRetentionService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        final var properties = new PurchaseRetentionProperties(Duration.ofDays(90), Duration.ofHours(1));
        service = new PurchaseRetentionService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), properties, new PurchaseRetentionMetrics(meterRegistry));
    }

    @Test
    @DisplayName("the cutoff is exactly now minus the configured retention")
    void givenAConfiguredRetention_whenCleaningUp_thenTheCutoffIsNowMinusRetention() {
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(0L);

        service.deleteExpiredPurchases();

        verify(repository).deleteByCreatedAtBefore(NOW.minus(Duration.ofDays(90)));
    }

    @Test
    @DisplayName("a repeated run over an already-cleaned table behaves identically")
    void givenTwoConsecutiveRuns_whenNothingChangedBetweenThem_thenBothDeleteTheSameCutoff() {
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(0L);

        service.deleteExpiredPurchases();
        service.deleteExpiredPurchases();

        verify(repository, times(2)).deleteByCreatedAtBefore(NOW.minus(Duration.ofDays(90)));
    }

    @Test
    @DisplayName("nothing past the cutoff records a zero deletion count, not an error")
    void givenNothingPastTheCutoff_whenCleaningUp_thenTheMetricRecordsZero() {
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(0L);

        service.deleteExpiredPurchases();

        assertEquals(0.0, meterRegistry.counter(DELETED_METRIC).count());
    }

    @Test
    @DisplayName("the deleted count is recorded on the metric, whatever it is")
    void givenPurchasesPastTheCutoff_whenCleaningUp_thenTheCountIsRecordedOnTheMetric() {
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(7L);

        service.deleteExpiredPurchases();

        assertEquals(7.0, meterRegistry.counter(DELETED_METRIC).count());
    }

    @Test
    @DisplayName("the query never touches purchases the store, not the service, is responsible for interpreting")
    void givenACleanupRun_whenDeleting_thenOnlyTheRepositoryIsInvokedOnce() {
        when(repository.deleteByCreatedAtBefore(any())).thenReturn(0L);

        service.deleteExpiredPurchases();

        verify(repository, times(1)).deleteByCreatedAtBefore(any());
        verify(repository, never()).findAll();
    }
}
