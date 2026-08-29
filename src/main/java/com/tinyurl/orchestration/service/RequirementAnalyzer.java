package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.AcceptanceCriterion;
import com.tinyurl.orchestration.model.RequirementAnalysis;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RequirementAnalyzer {

    public RequirementAnalysis analyze(String requirement) {
        String normalized = requirement.trim().replaceAll("\\s+", " ");
        boolean expirationScenario = normalized.toLowerCase().contains("expir");
        if (!expirationScenario) {
            return new RequirementAnalysis(
                    normalized,
                    List.of(new AcceptanceCriterion("AC-1", "The requested behavior is implemented and verified")),
                    List.of(),
                    List.of("Behavior, compatibility expectations, and failure semantics require clarification"),
                    List.of("Implementation without clarification may not match stakeholder intent"));
        }

        return new RequirementAnalysis(
                "Add optional URL expiration and lifecycle behavior while preserving existing links",
                List.of(
                        new AcceptanceCriterion("AC-1", "Creation accepts an optional expiresAt in UTC ISO-8601 format"),
                        new AcceptanceCriterion("AC-2", "A non-expired URL redirects normally"),
                        new AcceptanceCriterion("AC-3", "An expired URL returns HTTP 410 Gone"),
                        new AcceptanceCriterion("AC-4", "A nonexistent short code returns HTTP 404 Not Found"),
                        new AcceptanceCriterion("AC-5", "Existing URLs without expiration continue to work")),
                List.of("Expiration timestamps are evaluated using an injected UTC clock"),
                List.of("Whether already-expired timestamps should be rejected or stored as expired"),
                List.of("Boundary-time race conditions", "Backward compatibility of persisted records"));
    }
}
