package com.pedrolima.wexchange.domain;

import org.springframework.stereotype.Service;

/**
 * Deliberately broken. A framework annotation in the domain is what the
 * framework-free rule exists to reject, so {@code HexagonalBoundariesTest}
 * asserts that the rule rejects this class.
 *
 * <p>Lives in the architectureTest source set and never reaches production. Do
 * not "fix" it: a green build here would mean the rule has stopped working.
 */
@Service
public final class SpringInDomainFixture {
}
