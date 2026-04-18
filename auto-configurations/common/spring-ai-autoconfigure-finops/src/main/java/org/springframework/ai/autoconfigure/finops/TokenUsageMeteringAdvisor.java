package org.springframework.ai.autoconfigure.finops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;

/**
 * A {@link CallAroundAdvisor} that captures token-usage from the raw {@link ChatResponse}
 * and publishes Micrometer metrics, solving the observability gap when callers use
 * the high-level {@code .entity()} API which discards response metadata before it can
 * be inspected.
 *
 * <p>Metrics published:
 * <pre>
 * spring.ai.token.usage{model, operation, ai.token.type=[prompt|completion|total|cached]}
 * spring.ai.token.cost.estimated.usd{model, tier=[prompt|completion]}
 * </pre>
 *
 * <p>Addresses issues
 * <a href="https://github.com/spring-projects/spring-ai/issues/3895">#3895</a>,
 * <a href="https://github.com/spring-projects/spring-ai/issues/186">#186</a>,
 * <a href="https://github.com/spring-projects/spring-ai/issues/1506">#1506</a>
 * in spring-projects/spring-ai.
 */
public class TokenUsageMeteringAdvisor implements CallAroundAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageMeteringAdvisor.class);

    static final String METRIC_TOKEN_USAGE = "spring.ai.token.usage";
    static final String METRIC_TOKEN_COST = "spring.ai.token.cost.estimated.usd";

    /**
     * Run after all other advisors so token counts are measured on the final,
     * un-modified response.
     */
    private static final int ADVISOR_ORDER = Ordered.LOWEST_PRECEDENCE;

    private final MeterRegistry meterRegistry;
    private final FinOpsProperties properties;

    public TokenUsageMeteringAdvisor(MeterRegistry meterRegistry, FinOpsProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "TokenUsageMeteringAdvisor";
    }

    @Override
    public int getOrder() {
        return ADVISOR_ORDER;
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        AdvisedResponse advisedResponse = chain.nextAroundCall(advisedRequest);
        try {
            recordMetrics(advisedResponse);
        }
        catch (TokenBudgetExceededException ex) {
            throw ex;
        }
        catch (Exception ex) {
            log.warn("[FinOps] Failed to record token-usage metrics -- continuing without metrics.", ex);
        }
        return advisedResponse;
    }

    private void recordMetrics(AdvisedResponse advisedResponse) {
        ChatResponse chatResponse = advisedResponse.response();
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return;
        }

        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            log.debug("[FinOps] ChatResponse returned null Usage -- model may not report token counts.");
            return;
        }

        String model = resolveModelName(chatResponse);
        Tags baseTags = Tags.of("model", model, "operation", "chat");

        recordTokenCount(baseTags, "prompt", usage.getPromptTokens());
        recordTokenCount(baseTags, "completion", usage.getCompletionTokens());
        recordTokenCount(baseTags, "total", usage.getTotalTokens());

        Long cached = usage.getCachedTokens();
        if (cached != null && cached > 0) {
            recordTokenCount(baseTags, "cached", cached);
        }

        recordCost(model, usage);
        checkBudget(usage);
    }

    private void recordTokenCount(Tags baseTags, String type, Long count) {
        if (count == null || count == 0) return;
        Counter.builder(METRIC_TOKEN_USAGE)
                .tags(baseTags).tag("ai.token.type", type)
                .description("Cumulative LLM token usage by type")
                .register(meterRegistry).increment(count);
    }

    private void recordCost(String model, Usage usage) {
        double promptPrice = properties.pricePerMillionPromptTokens();
        double completionPrice = properties.pricePerMillionCompletionTokens();
        if (promptPrice == 0.0 && completionPrice == 0.0) return;

        Tags costTags = Tags.of("model", model);
        if (usage.getPromptTokens() != null && promptPrice > 0.0) {
            double cost = (usage.getPromptTokens() / 1_000_000.0) * promptPrice;
            Counter.builder(METRIC_TOKEN_COST).tags(costTags).tag("tier", "prompt")
                    .description("Estimated LLM cost in USD").register(meterRegistry).increment(cost);
        }
        if (usage.getCompletionTokens() != null && completionPrice > 0.0) {
            double cost = (usage.getCompletionTokens() / 1_000_000.0) * completionPrice;
            Counter.builder(METRIC_TOKEN_COST).tags(costTags).tag("tier", "completion")
                    .description("Estimated LLM cost in USD").register(meterRegistry).increment(cost);
        }
    }

    private void checkBudget(Usage usage) {
        double threshold = properties.budgetThresholdUsd();
        if (threshold <= 0.0 || usage.getTotalTokens() == null) return;

        Double cumulativeCost = meterRegistry.find(METRIC_TOKEN_COST).counters()
                .stream().mapToDouble(c -> c.count()).sum();

        if (cumulativeCost > threshold) {
            log.warn("[FinOps] Token budget threshold of {} USD exceeded. Cumulative cost: {}",
                    threshold, String.format("%.4f", cumulativeCost));
            throw new TokenBudgetExceededException(
                    String.format("LLM token budget of %.2f USD exceeded (current: %.4f). "
                            + "Increase spring.ai.finops.budget-threshold-usd to continue.",
                            threshold, cumulativeCost));
        }
    }

    private static String resolveModelName(ChatResponse chatResponse) {
        try {
            String model = chatResponse.getMetadata().getModel();
            return (model != null && !model.isBlank()) ? model : "unknown";
        }
        catch (Exception e) {
            return "unknown";
        }
    }

}
