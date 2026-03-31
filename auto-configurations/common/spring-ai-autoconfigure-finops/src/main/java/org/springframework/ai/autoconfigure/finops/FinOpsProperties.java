package org.springframework.ai.autoconfigure.finops;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Configuration properties for the Spring AI FinOps token-usage observability module.
 *
 * <pre>
 * spring.ai.finops.enabled=true
 * spring.ai.finops.price-per-million-prompt-tokens=3.00
 * spring.ai.finops.price-per-million-completion-tokens=15.00
 * spring.ai.finops.budget-threshold-usd=10.00
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "spring.ai.finops")
public record FinOpsProperties(

        /** Enable or disable the FinOps metering advisor. */
        @DefaultValue("false")
        boolean enabled,

        /**
         * Price in USD per 1,000,000 prompt (input) tokens.
         * Set to 0 to disable cost estimation (default).
         * Example: GPT-4o = 2.50, GPT-4o-mini = 0.15
         */
        @PositiveOrZero
        @DefaultValue("0.0")
        double pricePerMillionPromptTokens,

        /**
         * Price in USD per 1,000,000 completion (output) tokens.
         * Set to 0 to disable cost estimation (default).
         * Example: GPT-4o = 10.00, GPT-4o-mini = 0.60
         */
        @PositiveOrZero
        @DefaultValue("0.0")
        double pricePerMillionCompletionTokens,

        /**
         * Cumulative estimated USD cost at which the advisor will throw a
         * TokenBudgetExceededException, acting as a "light switch" to
         * stop further LLM usage. Set to 0 (default) to disable budget enforcement.
         */
        @PositiveOrZero
        @DefaultValue("0.0")
        double budgetThresholdUsd) {
}
