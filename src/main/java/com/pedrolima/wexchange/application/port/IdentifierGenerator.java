package com.pedrolima.wexchange.application.port;

/**
 * Supplies identifiers for newly created records.
 *
 * <p>Identity is a dependency, not an ambient capability. Behind this port a
 * test supplies a fixed value and asserts the exact identifier that reaches the
 * response and the database.
 */
public interface IdentifierGenerator {

    String newIdentifier();
}
