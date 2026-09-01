package com.pedrolima.wexchange.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Automated tests must never reach the public internet: a suite that depends on
 * a live provider is slow, flaky, and silently green when the provider is down.
 *
 * <p>This rule is checked against the compiled unit and integration suites, not
 * against source text, so a helper class or a lambda cannot smuggle a real
 * connection past it. Calls that merely <em>build</em> a client are allowed;
 * what is forbidden is opening a connection. Outbound HTTP in production code
 * goes through an injected {@link HttpClient}, which every suite stubs.
 */
class OfflineTestsArchitectureTest {

    private static final List<Path> TEST_CLASS_DIRECTORIES = List.of(
            Path.of("build", "classes", "java", "test"),
            Path.of("build", "classes", "java", "integrationTest"));

    private static JavaClasses testClasses;

    @BeforeAll
    static void importTestClasses() {
        final var present = TEST_CLASS_DIRECTORIES.stream().filter(Files::isDirectory).toList();

        Assertions.assertEquals(
                TEST_CLASS_DIRECTORIES.size(),
                present.size(),
                "Expected compiled test classes in " + TEST_CLASS_DIRECTORIES
                        + "; without them this rule would pass vacuously.");

        testClasses = new ClassFileImporter().importPaths(present);

        Assertions.assertTrue(testClasses.iterator().hasNext(), "No test classes were imported.");
    }

    @Test
    @DisplayName("no test opens a network connection")
    void givenTestSuites_whenCheckingOutboundCalls_thenNoneOpensAConnection() {
        final ArchRule rule = noClasses()
                .should()
                .callMethod(URL.class, "openConnection")
                .orShould()
                .callMethod(URL.class, "openStream")
                .orShould()
                .callConstructor(Socket.class, String.class, int.class)
                .orShould()
                .callMethod(URL.class, "getContent")
                .orShould()
                .callMethod(HttpClient.class, "newHttpClient")
                .because("automated tests must run offline and deterministically; stub the provider instead");

        rule.check(testClasses);
    }
}
