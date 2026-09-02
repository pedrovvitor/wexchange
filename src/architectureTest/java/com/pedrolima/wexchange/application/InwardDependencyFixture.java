package com.pedrolima.wexchange.application;

import com.pedrolima.wexchange.adapter.out.persistence.PurchaseJpaEntity;

/**
 * Deliberately broken. An application class reaching for a persistence entity is
 * exactly the dependency the layered rule exists to reject, so
 * {@code HexagonalBoundariesTest} asserts that the rule rejects this class.
 *
 * <p>Lives in the architectureTest source set and never reaches production. Do
 * not "fix" it: a green build here would mean the rule has stopped working.
 */
public final class InwardDependencyFixture {

    private InwardDependencyFixture() {
    }

    public static String leak(final PurchaseJpaEntity entity) {
        return entity.getId();
    }
}
