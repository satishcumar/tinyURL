package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.AcceptanceCriterion;
import com.tinyurl.orchestration.model.RequirementAnalysis;
import com.tinyurl.orchestration.model.RepositoryImpact;
import com.tinyurl.orchestration.model.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RequirementAnalyzer {

    public RequirementAnalysis analyze(String requirement) {
        String normalized = requirement.trim().replaceAll("\\s+", " ");
        boolean migrationScenario = normalized.toLowerCase().contains("flyway") ||
                normalized.toLowerCase().contains("create-drop") ||
                normalized.toLowerCase().contains("migration");
        if (migrationScenario) {
            return new RequirementAnalysis(
                    ScenarioType.BROWNFIELD,
                    "Replace Hibernate schema creation with versioned Flyway migrations while preserving existing data",
                    List.of(
                            new AcceptanceCriterion("AC-1", "A clean database is created by Flyway migration V1"),
                            new AcceptanceCriterion("AC-2", "An existing schema is baselined without losing rows"),
                            new AcceptanceCriterion("AC-3", "Hibernate validates the migrated schema and never creates or drops it"),
                            new AcceptanceCriterion("AC-4", "A failed migration safe-stops before application traffic is accepted"),
                            new AcceptanceCriterion("AC-5", "Backup and recovery instructions are recorded before schema change")),
                    List.of("The existing H2 schema already matches the current UrlMapping entity"),
                    List.of("Production backup location and retention policy require environment-specific approval"),
                    List.of("Incorrect baselining could skip required DDL", "Schema drift could prevent application startup"),
                    List.of(
                            new RepositoryImpact("pom.xml", "Add Flyway runtime dependency", "Dependency compatibility"),
                            new RepositoryImpact("application-local.yaml", "Replace create-drop with validate and enable baselining", "Startup failure on schema drift"),
                            new RepositoryImpact("application-test.yaml", "Run tests against migrated schema", "Test isolation"),
                            new RepositoryImpact("db/migration", "Own schema through immutable versioned SQL", "Migration correctness"),
                            new RepositoryImpact("UrlMapping", "Entity must match migrated columns and constraints", "ORM/schema mismatch")));
        }
        boolean expirationScenario = normalized.toLowerCase().contains("expir");
        if (!expirationScenario) {
            return new RequirementAnalysis(
                    ScenarioType.AMBIGUOUS,
                    normalized,
                    List.of(new AcceptanceCriterion("AC-1", "The requested behavior is implemented and verified")),
                    List.of(),
                    List.of("Behavior, compatibility expectations, and failure semantics require clarification"),
                    List.of("Implementation without clarification may not match stakeholder intent"),
                    List.of());
        }

        return new RequirementAnalysis(
                ScenarioType.GREENFIELD,
                "Add optional URL expiration and lifecycle behavior while preserving existing links",
                List.of(
                        new AcceptanceCriterion("AC-1", "Creation accepts an optional expiresAt in UTC ISO-8601 format"),
                        new AcceptanceCriterion("AC-2", "A non-expired URL redirects normally"),
                        new AcceptanceCriterion("AC-3", "An expired URL returns HTTP 410 Gone"),
                        new AcceptanceCriterion("AC-4", "A nonexistent short code returns HTTP 404 Not Found"),
                        new AcceptanceCriterion("AC-5", "Existing URLs without expiration continue to work")),
                List.of("Expiration timestamps are evaluated using an injected UTC clock"),
                List.of("Whether already-expired timestamps should be rejected or stored as expired"),
                List.of("Boundary-time race conditions", "Backward compatibility of persisted records"),
                List.of(
                        new RepositoryImpact("TinyURLController", "Accept optional expiresAt", "API compatibility"),
                        new RepositoryImpact("UrlMapping", "Persist nullable expiration", "Existing-row compatibility"),
                        new RepositoryImpact("UrlServiceImpl", "Enforce lifecycle behavior", "Boundary-time behavior")));
    }
}
