package org.springframework.ai.autoconfigure.finops;

/**
 * Thrown by {@link TokenUsageMeteringAdvisor} when the cumulative estimated LLM spend
 * exceeds the configured {@link FinOpsProperties} budget threshold.
 *
 * <p>This implements the FinOps "light switch" pattern: instead of silently letting
 * token costs accumulate, the application receives an explicit, catchable signal
 * that it can handle with a fallback, circuit-breaker, or alert.
 *
 * <p>Addresses:
 * <ul>
 *   <li><a href="https://github.com/spring-projects/spring-ai/issues/186">#186</a></li>
 *   <li><a href="https://github.com/spring-projects/spring-ai/issues/3895">#3895</a></li>
 * </ul>
 */
public class TokenBudgetExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code TokenBudgetExceededException} with the specified message.
     *
     * @param message detail message describing the exceeded budget
     */
    public TokenBudgetExceededException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code TokenBudgetExceededException} with the specified message
     * and cause.
     *
     * @param message detail message describing the exceeded budget
     * @param cause   the underlying cause (may be null)
     */
    public TokenBudgetExceededException(String message, Throwable cause) {
        super(message, cause);
    }

}
