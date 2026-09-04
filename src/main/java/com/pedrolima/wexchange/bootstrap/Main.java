package com.pedrolima.wexchange.bootstrap;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code scanBasePackages} controls component scanning only. Spring Boot's
 * automatic JPA repository and entity scanning instead defaults to this
 * class's own package - {@code bootstrap}, a sibling of
 * {@code adapter.out.persistence}, not an ancestor of it - so without the two
 * annotations below, no repository is discoverable and the application fails
 * to start the moment anything needs one. No existing test caught this because
 * none boot a real {@code @SpringBootApplication} context; they wire classes
 * directly or use {@code @DataJpaTest} with its own explicit scan overrides.
 */
@SpringBootApplication(scanBasePackages = "com.pedrolima.wexchange")
@EnableJpaRepositories(basePackages = "com.pedrolima.wexchange.adapter.out.persistence")
@EntityScan(basePackages = "com.pedrolima.wexchange.adapter.out.persistence")
@OpenAPIDefinition(info = @Info(
        title = "Wexchange API",
        version = "v1",
        description = "Currency quotation and purchase-conversion API."
))
@EnableRetry
@EnableScheduling
@EnableAsync
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
