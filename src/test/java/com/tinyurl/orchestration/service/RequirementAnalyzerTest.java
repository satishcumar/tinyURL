package com.tinyurl.orchestration.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementAnalyzerTest {
    private final RequirementAnalyzer analyzer = new RequirementAnalyzer();

    @Test
    void normalizesExpirationRequirementIntoReviewableCriteria() {
        var result = analyzer.analyze(" Add URL expiration and lifecycle management ");

        assertThat(result.acceptanceCriteria()).hasSize(5);
        assertThat(result.acceptanceCriteria()).extracting("id")
                .containsExactly("AC-1", "AC-2", "AC-3", "AC-4", "AC-5");
        assertThat(result.ambiguities()).isNotEmpty();
        assertThat(result.risks()).contains("Backward compatibility of persisted records");
    }

    @Test
    void identifiesBrownfieldMigrationImpactAndDataRisk() {
        var result = analyzer.analyze(
                "Replace create-drop with Flyway migrations while preserving existing data");

        assertThat(result.scenario().name()).isEqualTo("BROWNFIELD");
        assertThat(result.acceptanceCriteria()).hasSize(5);
        assertThat(result.repositoryImpacts()).extracting("component")
                .contains("pom.xml", "application-local.yaml", "db/migration", "UrlMapping");
        assertThat(result.risks()).anyMatch(risk -> risk.contains("baselining"));
    }

    @Test
    void boundsAmbiguousAnalyticsWithPrivacyAndAvailabilityCriteria() {
        var result = analyzer.analyze("Provide richer analytics");

        assertThat(result.scenario().name()).isEqualTo("AMBIGUOUS");
        assertThat(result.acceptanceCriteria()).hasSize(5);
        assertThat(result.assumptions()).anyMatch(value -> value.contains("aggregate metrics"));
        assertThat(result.ambiguities()).anyMatch(value -> value.contains("Visitor-level"));
        assertThat(result.repositoryImpacts()).extracting("component")
                .contains("UrlAnalyticsResponse", "UrlServiceImpl", "redirect endpoint");
    }
}
