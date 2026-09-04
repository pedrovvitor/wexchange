package com.pedrolima.wexchange.adapter.in.web;

import com.pedrolima.wexchange.adapter.out.persistence.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End to end against a real Postgres and a real {@code CreatePurchaseService}
 * (issue #18): the parts that only a genuine database - not a mock - can
 * actually prove, namely that the unique constraint on {@code idempotency_key}
 * is what makes concurrent identical requests collapse into exactly one
 * purchase.
 *
 * <p>Rate limiting (issue #17) is overridden to be generous: this suite's own
 * twenty-concurrent-request test would otherwise trip a real 429 on request
 * volume alone, which is not what it is testing.
 */
class PurchaseIdempotencyIT extends AbstractPostgresApplicationIT {

    @DynamicPropertySource
    static void generousRateLimits(final DynamicPropertyRegistry registry) {
        registry.add("app.abuse-control.global.capacity", () -> "10000");
        registry.add("app.abuse-control.global.refill-tokens", () -> "10000");
        registry.add("app.abuse-control.purchase-creation.capacity", () -> "10000");
        registry.add("app.abuse-control.purchase-creation.refill-tokens", () -> "10000");
    }

    private static final String CREATE_BODY = """
            {"description":"Idempotent purchase","date":"2024-01-31","amount":10.00}
            """;

    private static final String DIFFERENT_BODY = """
            {"description":"A different purchase","date":"2024-02-01","amount":20.00}
            """;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Test
    void givenNoIdempotencyKey_whenCreatingTwiceWithTheSamePayload_thenTwoDistinctPurchasesAreCreated() {
        final var first = post(CREATE_BODY, null);
        final var second = post(CREATE_BODY, null);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertNotEquals(id(first), id(second));
    }

    @Test
    void givenAMalformedIdempotencyKey_whenCreating_then400WithoutCreatingAnything() {
        final var response = post(CREATE_BODY, "a key with spaces");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().contains("validation-failed"));
    }

    @Test
    void givenTheSameKeyAndPayloadTwice_whenCreating_thenTheSecondRequestReplaysTheFirstResponse() {
        final String key = "replay-" + UUID.randomUUID();
        final long before = purchaseRepository.count();

        final var first = post(CREATE_BODY, key);
        final var second = post(CREATE_BODY, key);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertEquals(first.getBody(), second.getBody());
        assertEquals(first.getHeaders().getLocation(), second.getHeaders().getLocation());
        // A running count, not an absolute one: this suite shares one database
        // across test methods, none of which roll back (@SpringBootTest, unlike
        // @DataJpaTest, gives every test a real committed transaction).
        assertEquals(before + 1, purchaseRepository.count());
    }

    @Test
    void givenTheSameKeyWithADifferentPayload_whenCreating_then409WithoutCreatingASecondPurchase() {
        final String key = "conflict-" + UUID.randomUUID();

        final var first = post(CREATE_BODY, key);
        final var second = post(DIFFERENT_BODY, key);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
        assertTrue(second.getBody().contains("idempotency-key-conflict"));
    }

    @Test
    void givenTwentyConcurrentRequestsWithTheSameKeyAndPayload_whenCreating_thenExactlyOnePurchaseIsCreated()
            throws InterruptedException {
        final String key = "concurrent-" + UUID.randomUUID();
        final long before = purchaseRepository.count();
        final int callers = 20;
        final ExecutorService pool = Executors.newFixedThreadPool(callers);
        final CountDownLatch ready = new CountDownLatch(callers);
        final CountDownLatch go = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(callers);
        final List<AtomicReference<ResponseEntity<String>>> responses = new java.util.ArrayList<>();
        for (int i = 0; i < callers; i++) {
            responses.add(new AtomicReference<>());
        }

        for (int i = 0; i < callers; i++) {
            final int index = i;
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await(10, TimeUnit.SECONDS);
                    responses.get(index).set(post(CREATE_BODY, key));
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "callers never reached the starting line");
        go.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS), "not every concurrent request finished in time");
        pool.shutdown();

        assertEquals(before + 1, purchaseRepository.count(),
                "twenty concurrent identical requests must create exactly one purchase");
        final var firstId = id(responses.get(0).get());
        for (final var response : responses) {
            assertEquals(HttpStatus.CREATED, response.get().getStatusCode());
            assertEquals(firstId, id(response.get()));
        }
    }

    private ResponseEntity<String> post(final String body, final String idempotencyKey) {
        final var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return restTemplate.postForEntity(baseUrl("/v1/purchases"), new HttpEntity<>(body, headers), String.class);
    }

    private static String id(final ResponseEntity<String> response) {
        final String body = response.getBody();
        final int start = body.indexOf("\"id\":\"") + 6;
        return body.substring(start, body.indexOf('"', start));
    }
}
