package com.pedrolima.wexchange.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Dependency direction, enforced. See docs/adr/0001-hexagonal-boundaries.md for
 * what each area owns and why.
 *
 * <p>Every rule here is checked against a fixture carrying a deliberate
 * violation as well as against production code, so a rule that has quietly
 * stopped matching anything fails rather than passing.
 */
class HexagonalBoundariesTest {

    private static final String BASE = "com.pedrolima.wexchange";

    private static final Path MAIN_CLASSES = Path.of("build", "classes", "java", "main");

    private static final Path FIXTURE_CLASSES = Path.of("build", "classes", "java", "architectureTest");

    private static JavaClasses production;

    /**
     * Imported by path rather than by package name on purpose. The boundary
     * fixtures below deliberately sit inside {@code domain} and {@code application}
     * so the rules can reach them; a package-name import would pull them into the
     * production set and fail the very rules they exist to prove.
     */
    @BeforeAll
    static void importProductionClasses() {
        Assertions.assertTrue(Files.isDirectory(MAIN_CLASSES), "Compiled main classes not found at " + MAIN_CLASSES);

        production = new ClassFileImporter().importPath(MAIN_CLASSES);

        Assertions.assertTrue(production.iterator().hasNext(), "No production classes were imported.");
    }

    private static ArchRule layers() {
        return layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("domain").definedBy(BASE + ".domain..")
                .layer("application").definedBy(BASE + ".application..")
                .layer("web").definedBy(BASE + ".adapter.in.web..")
                .layer("persistence").definedBy(BASE + ".adapter.out.persistence..")
                .layer("fiscal").definedBy(BASE + ".adapter.out.fiscal..")
                .layer("bootstrap").definedBy(BASE + ".bootstrap..")

                .whereLayer("bootstrap").mayNotBeAccessedByAnyLayer()
                .whereLayer("web").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("persistence").mayOnlyBeAccessedByLayers("bootstrap", "fiscal", "web")
                .whereLayer("fiscal").mayOnlyBeAccessedByLayers("bootstrap")
                .whereLayer("application").mayOnlyBeAccessedByLayers(
                        "web", "persistence", "fiscal", "bootstrap")
                .whereLayer("domain").mayOnlyBeAccessedByLayers(
                        "application", "web", "persistence", "fiscal", "bootstrap");
    }

    @Test
    @DisplayName("dependencies point inward")
    void givenProductionCode_whenCheckingLayers_thenDependenciesPointInward() {
        layers().check(production);
    }

    @Test
    @DisplayName("the layer rule rejects an adapter reached from the application")
    void givenAnInwardDependencyOnAnAdapter_whenCheckingLayers_thenItIsRejected() {
        assertRejects(layers(), "InwardDependencyFixture");
    }

    @Test
    @DisplayName("domain and application import no framework or persistence types")
    void givenDomainAndApplication_whenCheckingImports_thenNoFrameworkTypesAppear() {
        frameworkFreeCore().check(production);
    }

    @Test
    @DisplayName("the framework rule rejects a domain class importing Spring")
    void givenDomainImportingSpring_whenChecking_thenItIsRejected() {
        assertRejects(frameworkFreeCore(), "SpringInDomainFixture");
    }

    private static ArchRule frameworkFreeCore() {
        return noClasses()
                .that()
                .resideInAnyPackage(BASE + ".domain..", BASE + ".application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.jackson..",
                        "java.net.http..",
                        BASE + ".adapter..")
                .because("the core must stay independent of frameworks, transport, and storage");
    }

    @Test
    @DisplayName("no cycles between the top-level areas")
    void givenProductionCode_whenCheckingSlices_thenThereAreNoCycles() {
        slices().matching(BASE + ".(*)..").should().beFreeOfCycles().check(production);
    }

    /**
     * Runs a rule against a fixture package that is known to break it.
     *
     * <p>Without this, a rule whose package patterns stopped matching would go on
     * reporting success forever. The fixtures live in the architectureTest source
     * set and never reach the production artifact.
     */
    private static void assertRejects(final ArchRule rule, final String fixtureClass) {
        Assertions.assertTrue(Files.isDirectory(FIXTURE_CLASSES),
                "Compiled fixture classes not found at " + FIXTURE_CLASSES);

        final JavaClasses fixture = new ClassFileImporter().importPath(FIXTURE_CLASSES);

        Assertions.assertTrue(
                fixture.stream().anyMatch(c -> c.getName().endsWith(fixtureClass)),
                "Fixture not found: " + fixtureClass);

        final EvaluationResult result = rule.evaluate(fixture);

        Assertions.assertTrue(result.hasViolation(),
                "Expected " + fixtureClass + " to violate the rule, but it passed. "
                        + "The rule is no longer enforcing anything.");
    }
}
