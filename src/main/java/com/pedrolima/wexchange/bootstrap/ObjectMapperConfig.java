package com.pedrolima.wexchange.bootstrap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;

/**
 * The public-facing ObjectMapper, wired into Spring MVC.s message converter.
 *
 * <p>Strict on unknown properties on purpose: this is the boundary that
 * untrusted client input crosses. The Fiscal Data upstream adapter parses with
 * its own, separate, deliberately tolerant mapper in
 * {@code adapter.out.fiscal.JsonUtils} - an upstream provider may add fields
 * this application does not use yet, and that must not break ingestion. The two
 * boundaries have opposite trust levels and opposite policies for exactly that
 * reason.
 */
@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .registerModule(new JavaTimeModule())
                // Spring Boot applies this mixin automatically to its own
                // auto-configured ObjectMapper, but not to a hand-built one such
                // as this bean. Without it, ProblemDetail.getProperties() (code,
                // traceId, violations) never reaches the JSON body: the mixin is
                // what carries the @JsonAnyGetter/@JsonAnySetter that flattens it.
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
    }
}
