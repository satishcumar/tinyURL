package com.tinyurl.orchestration.service;

import com.tinyurl.orchestration.model.PolicyAction;
import com.tinyurl.orchestration.model.PolicyDecision;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component
public class PolicyEngine {

    private static final EnumSet<PolicyAction> AUTOMATIC = EnumSet.of(
            PolicyAction.INSPECT_REPOSITORY,
            PolicyAction.ANALYZE_REQUIREMENT,
            PolicyAction.CREATE_PLAN,
            PolicyAction.EDIT_FEATURE_CODE,
            PolicyAction.GENERATE_TESTS,
            PolicyAction.RUN_LOCAL_TESTS);

    private static final EnumSet<PolicyAction> APPROVAL_REQUIRED = EnumSet.of(
            PolicyAction.CHANGE_PUBLIC_API,
            PolicyAction.MODIFY_DATABASE_SCHEMA,
            PolicyAction.ADD_RUNTIME_DEPENDENCY,
            PolicyAction.COMMIT_CHANGES,
            PolicyAction.PUSH_BRANCH,
            PolicyAction.DEPLOY);

    public PolicyDecision evaluate(PolicyAction action) {
        if (AUTOMATIC.contains(action)) {
            return PolicyDecision.ALLOW;
        }
        if (APPROVAL_REQUIRED.contains(action)) {
            return PolicyDecision.REQUIRE_APPROVAL;
        }
        return PolicyDecision.DENY;
    }
}
