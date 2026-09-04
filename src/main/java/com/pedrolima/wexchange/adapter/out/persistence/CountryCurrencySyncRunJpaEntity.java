package com.pedrolima.wexchange.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The single, always-upserted row tracking the country-currency scheduled
 * sync's most recent run (issue #6) - see {@code CountryCurrencySyncRunTracker}.
 *
 * <p>{@code lastSuccessAt} and {@code lastFailureAt} are carried forward from
 * the previous row rather than derived solely from the current run, so a
 * failure never erases when the job last actually succeeded, and a success
 * never erases the record of a previous failure.
 */
@Entity
@Table(name = "country_currency_sync_run")
@Getter
public class CountryCurrencySyncRunJpaEntity {

    public static final String SINGLETON_ID = "singleton";

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SyncRunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    protected CountryCurrencySyncRunJpaEntity() {

    }

    private CountryCurrencySyncRunJpaEntity(
            final SyncRunStatus status,
            final Instant startedAt,
            final Instant finishedAt,
            final Instant lastSuccessAt,
            final Instant lastFailureAt,
            final String lastErrorMessage
    ) {
        this.id = SINGLETON_ID;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.lastSuccessAt = lastSuccessAt;
        this.lastFailureAt = lastFailureAt;
        this.lastErrorMessage = lastErrorMessage;
    }

    public static CountryCurrencySyncRunJpaEntity running(
            final Instant startedAt,
            final Optional<CountryCurrencySyncRunJpaEntity> previous
    ) {
        return new CountryCurrencySyncRunJpaEntity(
                SyncRunStatus.RUNNING, startedAt, null,
                previous.map(CountryCurrencySyncRunJpaEntity::getLastSuccessAt).orElse(null),
                previous.map(CountryCurrencySyncRunJpaEntity::getLastFailureAt).orElse(null),
                null);
    }

    public static CountryCurrencySyncRunJpaEntity succeeded(
            final Instant startedAt,
            final Instant finishedAt,
            final Optional<CountryCurrencySyncRunJpaEntity> previous
    ) {
        return new CountryCurrencySyncRunJpaEntity(
                SyncRunStatus.SUCCEEDED, startedAt, finishedAt, finishedAt,
                previous.map(CountryCurrencySyncRunJpaEntity::getLastFailureAt).orElse(null),
                null);
    }

    public static CountryCurrencySyncRunJpaEntity failed(
            final Instant startedAt,
            final Instant finishedAt,
            final String errorMessage,
            final Optional<CountryCurrencySyncRunJpaEntity> previous
    ) {
        return new CountryCurrencySyncRunJpaEntity(
                SyncRunStatus.FAILED, startedAt, finishedAt,
                previous.map(CountryCurrencySyncRunJpaEntity::getLastSuccessAt).orElse(null),
                finishedAt,
                errorMessage);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CountryCurrencySyncRunJpaEntity that = (CountryCurrencySyncRunJpaEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
