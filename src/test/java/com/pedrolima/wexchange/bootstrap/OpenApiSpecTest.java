package com.pedrolima.wexchange.bootstrap;

import com.pedrolima.wexchange.adapter.in.web.CountryCurrencyApi;
import com.pedrolima.wexchange.adapter.in.web.PurchaseApi;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the committed OpenAPI document without booting any Spring context:
 * the document is a static file, and both checks below only need to read it and
 * compare it against the {@code @RequestMapping} annotations already on the
 * classpath.
 *
 * <p>Regenerating the document (after changing a route) requires a running
 * instance. See docs/engineering/test-taxonomy.md for the exact steps.
 */
class OpenApiSpecTest {

    private static final Path SPEC_PATH = Path.of("docs", "openapi", "wexchange-v1.yaml");

    @Test
    @DisplayName("the committed document is syntactically and semantically valid OpenAPI 3")
    void givenCommittedSpec_whenParsing_thenItIsValid() throws Exception {
        final var content = Files.readString(SPEC_PATH);

        final var options = new ParseOptions();
        options.setResolve(true);
        final var result = new OpenAPIParser().readContents(content, null, options);

        assertTrue(result.getMessages().isEmpty(),
                "OpenAPI validation errors: " + result.getMessages());
        assertEquals("v1", result.getOpenAPI().getInfo().getVersion());
    }

    @Test
    @DisplayName("every documented path matches an implemented route, and vice versa")
    void givenCommittedSpec_whenComparedToTheApi_thenRoutesMatchExactly() throws Exception {
        final var content = Files.readString(SPEC_PATH);
        final var openApi = new OpenAPIParser().readContents(content, null, new ParseOptions()).getOpenAPI();

        final Set<String> documented = openApi.getPaths().keySet();
        final Set<String> implemented = new HashSet<>();
        implemented.addAll(springPaths(PurchaseApi.class));
        implemented.addAll(springPaths(CountryCurrencyApi.class));

        assertEquals(implemented, documented,
                "Committed spec has drifted from the implemented routes. Regenerate it - see"
                        + " docs/engineering/test-taxonomy.md.");
    }

    /** The full request paths ({@code @RequestMapping} base + method-level mapping) an API interface declares. */
    private static Set<String> springPaths(final Class<?> apiInterface) {
        final var base = AnnotatedElementUtils.findMergedAnnotation(apiInterface, RequestMapping.class);
        final var basePath = "/" + base.value()[0];

        final Set<String> paths = new HashSet<>();
        for (final Method method : apiInterface.getDeclaredMethods()) {
            for (final String suffix : mappingValue(method)) {
                paths.add(suffix.isEmpty() ? basePath : basePath + "/" + suffix);
            }
        }
        return paths;
    }

    private static String[] mappingValue(final Method method) {
        final var get = AnnotatedElementUtils.findMergedAnnotation(method, GetMapping.class);
        if (get != null) {
            return get.value().length > 0 ? get.value() : new String[] {""};
        }
        final var post = AnnotatedElementUtils.findMergedAnnotation(method, PostMapping.class);
        if (post != null) {
            return post.value().length > 0 ? post.value() : new String[] {""};
        }
        final var put = AnnotatedElementUtils.findMergedAnnotation(method, PutMapping.class);
        if (put != null) {
            return put.value().length > 0 ? put.value() : new String[] {""};
        }
        final var patch = AnnotatedElementUtils.findMergedAnnotation(method, PatchMapping.class);
        if (patch != null) {
            return patch.value().length > 0 ? patch.value() : new String[] {""};
        }
        final var delete = AnnotatedElementUtils.findMergedAnnotation(method, DeleteMapping.class);
        if (delete != null) {
            return delete.value().length > 0 ? delete.value() : new String[] {""};
        }
        return new String[0];
    }
}
