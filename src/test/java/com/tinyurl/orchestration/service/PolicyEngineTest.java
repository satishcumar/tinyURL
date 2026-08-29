package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.PolicyAction;
import com.tinyurl.orchestration.model.PolicyDecision;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEngineTest {
    private final PolicyEngine policyEngine = new PolicyEngine();

    @Test
    void permitsBoundedLocalEngineeringActions() {
        assertThat(policyEngine.evaluate(PolicyAction.ANALYZE_REQUIREMENT))
                .isEqualTo(PolicyDecision.ALLOW);
        assertThat(policyEngine.evaluate(PolicyAction.RUN_LOCAL_TESTS))
                .isEqualTo(PolicyDecision.ALLOW);
    }

    @Test
    void requiresApprovalForHighImpactActions() {
        assertThat(policyEngine.evaluate(PolicyAction.MODIFY_DATABASE_SCHEMA))
                .isEqualTo(PolicyDecision.REQUIRE_APPROVAL);
        assertThat(policyEngine.evaluate(PolicyAction.DEPLOY))
                .isEqualTo(PolicyDecision.REQUIRE_APPROVAL);
    }

    @Test
    void deniesProhibitedActions() {
        assertThat(policyEngine.evaluate(PolicyAction.FORCE_PUSH)).isEqualTo(PolicyDecision.DENY);
        assertThat(policyEngine.evaluate(PolicyAction.MODIFY_PRODUCTION_DATA)).isEqualTo(PolicyDecision.DENY);
    }
}
