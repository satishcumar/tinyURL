package com.tinyurl.orchestration.model;

public record RetryPolicy(int maxAttempts) {
    public RetryPolicy {
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 5");
        }
    }
}
