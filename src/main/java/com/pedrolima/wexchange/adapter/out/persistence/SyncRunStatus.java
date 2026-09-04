package com.pedrolima.wexchange.adapter.out.persistence;

/** The lifecycle of one scheduled-job run (issue #6), tracked for observability. */
public enum SyncRunStatus {
    RUNNING,
    SUCCEEDED,
    FAILED
}
