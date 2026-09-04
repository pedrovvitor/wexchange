package com.pedrolima.wexchange.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The CORS allowlist (issue #16). Empty by default: an unconfigured
 * deployment trusts no browser origin, rather than accidentally trusting
 * every origin. A real frontend deployment sets {@code app.cors.allowed-origins}
 * explicitly.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins != null ? List.copyOf(allowedOrigins) : List.of();
    }
}
