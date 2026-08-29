package com.tinyurl.orchestration.model;

import java.util.List;

public record RequirementAnalysis(
        ScenarioType scenario,
        String normalizedRequirement,
        List<AcceptanceCriterion> acceptanceCriteria,
        List<String> assumptions,
        List<String> ambiguities,
        List<String> risks,
        List<RepositoryImpact> repositoryImpacts) {
}
