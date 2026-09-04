package com.pedrolima.wexchange.bootstrap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * The anonymous API security baseline (issue #16; see ADR 0002 for the full
 * threat model). Wexchange has no authenticated user, so this configures
 * exactly three things a public, anonymous, synthetic-data API still needs:
 *
 * <ul>
 *   <li>an explicit CORS allowlist, credential-free;
 *   <li>the header baseline a browser client can act on (MIME-sniffing,
 *       clickjacking, referrer leakage, powerful-feature access);
 *   <li>an explicit "everything is public" authorization decision, so the
 *       absence of authentication reads as a deliberate choice rather than a
 *       gap nobody configured.
 * </ul>
 *
 * <p>Rate limiting, request-size limits, and abuse controls are issue #17.
 * Trusted-proxy handling is {@code server.forward-headers-strategy} in
 * {@code application.yml}, not here: there is no reverse proxy in front of
 * this application today, so the correct posture is to trust no forwarded
 * header at all, which is what leaving that property at its default achieves.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // No session, no cookie, nothing a cross-site form could replay: CSRF
                // protects a cookie-authenticated session, which this API never has.
                .csrf(csrf -> csrf.disable())
                // Anonymous by design (ADR 0002), not an oversight: the product is a
                // public quotation API over synthetic data, with no user to authenticate.
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .headers(this::configureHeaders);
        return http.build();
    }

    private void configureHeaders(final HeadersConfigurer<HttpSecurity> headers) {
        headers
                // X-Content-Type-Options: nosniff.
                .contentTypeOptions(contentTypeOptions -> {
                })
                // X-Frame-Options: DENY - this API is never meant to be framed.
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .referrerPolicy(referrerPolicy -> referrerPolicy
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // No browser feature this API's responses are ever rendered into needs
                // camera, microphone, or geolocation access.
                .addHeaderWriter(new StaticHeadersWriter(
                        "Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
        // HSTS is Spring Security's default and deliberately left as-is: its writer
        // only ever adds Strict-Transport-Security to an already-secure (HTTPS)
        // response, so it stays silent over today's plain-HTTP topology and only
        // takes effect once a verified HTTPS deployment profile terminates TLS in
        // front of this application. Preload is a separate, later decision (ADR 0002).
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(final CorsProperties properties) {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Idempotency-Key"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
