package com.pedrolima.wexchange.usecases;

/**
 * Supplies identifiers for newly created records.
 *
 * <p>Identity is a dependency, not an ambient capability. Calling
 * {@code UUID.randomUUID()} inside a use case makes its output unpredictable and
 * forces tests to assert around the identifier instead of on it. Behind this
 * port a test can supply a fixed sequence and assert the exact value that
 * reaches the response and the database.
 */
public interface IdentifierGenerator {

    String newIdentifier();
}
