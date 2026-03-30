package org.springframework.ai.autoconfigure.finops;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
@ConfigurationProperties(prefix = "spring.ai.finops")
public class FinOpsProperties {

    /** Enable or disable the FinOps metering advisor. */
    private boolean enabled = false;

    /**
     * Price in USD per 1,000,000 prompt (input) tokens.
     * Set to 0 to disable cost estimation (default).
     * Example: GPT-4o = 2.50, GPT-4o-mini = 0.15
     */
    private double pricePerMillionPromptTokens = 0.0;

    /**
     * Price in USD per 1,000,000 completion (output) tokens.
     * Set to 0 to disable cost estimation (default).
     * Example: GPT-4o = 10.00, GPT-4o-mini = 0.60
     */
    private double pricePerMillionCompletionTokens = 0.0;

    /**
     * Cumulative estimated USD cost at which the advisor will throw a
     * TokenBudgetExceededException, acting as a "light switch" to
     * stop further LLM usage. Set to 0 (default) to disable budget enforcement.
     */
    private double budgetThresholdUsd = 0.0;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double getPricePerMillionPromptTokens() { return pricePerMillionPromptTokens; }
    public void setPricePerMillionPromptTokens(double v) { this.pricePerMillionPromptTokens = v; }

    public double getPricePerMillionCompletionTokens() { return pricePerMillionCompletionTokens; }
    public void setPricePerMillionCompletionTokens(double v) { this.pricePerMillionCompletionTokens = v; }

    public double getBudgetThresholdUsd() { return budgetThresholdUsd; }
    public void setBudgetThresholdUsd(double v) { this.budgetThresholdUsd = v; }
}
