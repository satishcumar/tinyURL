package com.tinyurl.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<UrlMapping, Long> {
    Optional<UrlMapping> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UrlMapping mapping
               set mapping.redirectCount = mapping.redirectCount + 1,
                   mapping.lastAccessedAt = :accessedAt
             where mapping.shortCode = :shortCode
            """)
    int recordRedirect(
            @Param("shortCode") String shortCode,
            @Param("accessedAt") Instant accessedAt);
}
