package com.tinyurl.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RedirectEventRepository extends JpaRepository<RedirectEvent, Long> {

    List<RedirectEvent> findByShortCodeAndOccurredAtGreaterThanEqualOrderByOccurredAtAsc(
            String shortCode,
            Instant from);
}
