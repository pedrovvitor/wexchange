package com.pedrolima.wexchange.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * Coding-hygiene fitness functions over production code.
 *
 * <p>These rules are deliberately independent of the target hexagonal layering.
 * Dependency-direction and adapter-isolation rules are owned by issue #15 and
 * belong in their own rule class so that the two concerns fail separately.
 */
class CodingRulesArchitectureTest {

    private static final String PRODUCTION_PACKAGE = "com.pedrolima.wexchange";

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
                .importPackages(PRODUCTION_PACKAGE);

        // A silently empty import would make every rule below vacuously true.
        Assertions.assertTrue(
                productionClasses.iterator().hasNext(),
                "No production classes were imported from " + PRODUCTION_PACKAGE
                        + "; the architectureTest classpath is misconfigured.");
    }

    @Test
    @DisplayName("production code writes diagnostics through the logging facade, not standard streams")
    void givenProductionCode_whenCheckingStandardStreams_thenNoneIsUsed() {
        NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(productionClasses);
    }

    @Test
    @DisplayName("production code does not throw generic exception types")
    void givenProductionCode_whenCheckingThrownExceptions_thenNoneIsGeneric() {
        NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS.check(productionClasses);
    }

    @Test
    @DisplayName("production code uses SLF4J rather than java.util.logging")
    void givenProductionCode_whenCheckingLoggingApi_thenJavaUtilLoggingIsAbsent() {
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(productionClasses);
    }

    /**
     * Green rather than frozen. Two {@code @Value} fields were recorded as debt
     * when this gate was introduced; issue #1 replaced them with constructor
     * injection, so the violation store was emptied and deleted in the same
     * change. The rule now stands on its own.
     */
    @Test
    @DisplayName("collaborators are injected through constructors, never into fields")
    void givenProductionCode_whenCheckingInjection_thenNoFieldInjectionIsUsed() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(productionClasses);
    }

    @Test
    @DisplayName("use cases and entities never open outbound connections themselves")
    void givenProductionCode_whenCheckingOutboundHttp_thenOnlyAdapterCodeOpensConnections() {
        final ArchRule rule = noClasses()
                .that()
                .resideInAnyPackage(
                        PRODUCTION_PACKAGE + ".usecases..",
                        PRODUCTION_PACKAGE + ".entities..")
                .should()
                .accessClassesThat()
                .resideInAnyPackage("java.net.http..")
                .because("outbound HTTP belongs to adapter code so that automated tests can stub it offline");

        rule.check(productionClasses);
    }
}
