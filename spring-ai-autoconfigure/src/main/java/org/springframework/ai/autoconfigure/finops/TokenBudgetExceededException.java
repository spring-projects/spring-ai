package org.springframework.ai.autoconfigure.finops;

/**
 * Thrown by TokenUsageMeteringAdvisor when the cumulative estimated LLM spend
 * exceeds the configured FinOpsProperties budget threshold.
 *
 * This implements the FinOps "light switch" pattern: instead of silently letting
 * token costs accumulate, the application receives an explicit, catchable signal
 * that it can handle with a fallback, circuit-breaker, or alert.
 *
 * Addresses: https://github.com/spring-projects/spring-ai/issues/186
 *            https://github.com/spring-projects/spring-ai/issues/3895
 */
public class TokenBudgetExceededException extends RuntimeException {

    public TokenBudgetExceededException(String message) {
        super(message);
    }
}
