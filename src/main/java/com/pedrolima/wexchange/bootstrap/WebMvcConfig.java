package com.pedrolima.wexchange.bootstrap;

import com.pedrolima.wexchange.adapter.in.web.ratelimit.AbuseControlInterceptor;
import com.pedrolima.wexchange.adapter.in.web.ratelimit.AbuseControlMetrics;
import com.pedrolima.wexchange.adapter.in.web.ratelimit.AbuseControlProperties;
import com.pedrolima.wexchange.adapter.in.web.ratelimit.PerKeyRateLimiter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the anonymous-abuse controls (issue #17) on every {@code /v1/**} route. */
@Configuration
@EnableConfigurationProperties(AbuseControlProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final AbuseControlProperties properties;

    private final PerKeyRateLimiter rateLimiter;

    private final AbuseControlMetrics metrics;

    public WebMvcConfig(
            final AbuseControlProperties properties,
            final PerKeyRateLimiter rateLimiter,
            final AbuseControlMetrics metrics
    ) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(abuseControlInterceptor()).addPathPatterns("/v1/**");
    }

    @Bean
    public AbuseControlInterceptor abuseControlInterceptor() {
        return new AbuseControlInterceptor(properties, rateLimiter, metrics);
    }
}
