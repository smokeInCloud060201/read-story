package com.example.readstory.common.dto;

public record RetrySpec(
        int maxAttempts,
        long baseDelayMs,
        long maxDelayMs
) {
    public static RetrySpec of(int maxAttempts, long baseDelayMs, long maxDelayMs) {
        return new RetrySpec(maxAttempts, baseDelayMs, maxDelayMs);
    }
}
