package com.tinyurl.integration;

import com.tinyurl.domain.UrlMapping;
import com.tinyurl.domain.UrlRepository;
import com.tinyurl.service.UrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentRedirectIntegrationTest {

    private static final int REDIRECT_REQUESTS = 40;

    private final UrlRepository repository;
    private final UrlService service;

    @Autowired
    ConcurrentRedirectIntegrationTest(UrlRepository repository, UrlService service) {
        this.repository = repository;
        this.service = service;
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.saveAndFlush(new UrlMapping(
                "parallel",
                "https://example.com",
                Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void concurrentRedirectsDoNotLoseCountUpdates() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        try {
            for (int i = 0; i < REDIRECT_REQUESTS; i++) {
                results.add(executor.submit(() -> {
                    startGate.await();
                    return service.resolveAndRecordRedirect("parallel");
                }));
            }

            startGate.countDown();
            for (Future<String> result : results) {
                assertEquals("https://example.com", result.get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        UrlMapping updated = repository.findByShortCode("parallel").orElseThrow();
        assertEquals(REDIRECT_REQUESTS, updated.getRedirectCount());
    }
}
