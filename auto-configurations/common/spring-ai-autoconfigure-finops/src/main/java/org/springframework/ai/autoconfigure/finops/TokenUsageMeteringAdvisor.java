/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.autoconfigure.finops;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;

/**
 * A {@link CallAdvisor} that captures token-usage from the raw {@link ChatResponse} and
 * publishes Micrometer metrics, solving the observability gap when callers use the
 * high-level {@code .entity()} API which discards response metadata before it can be
 * inspected.
 *
 * <p>
 * Metrics published: <pre>
 * spring.ai.token.usage{model, operation, ai.token.type=[prompt|completion|total|cached|cache_write]}
 * spring.ai.token.cost.estimated.usd{model, tier=[prompt|completion]}
 * </pre>
 *
 * <p>
 * Addresses issues
 * <a href="https://github.com/spring-projects/spring-ai/issues/3895">#3895</a>,
 * <a href="https://github.com/spring-projects/spring-ai/issues/186">#186</a>,
 * <a href="https://github.com/spring-projects/spring-ai/issues/1506">#1506</a> in
 * spring-projects/spring-ai.
 */
public class TokenUsageMeteringAdvisor implements CallAdvisor {

	private static final Logger log = LoggerFactory.getLogger(TokenUsageMeteringAdvisor.class);

	static final String METRIC_TOKEN_USAGE = "spring.ai.token.usage";

	static final String METRIC_TOKEN_COST = "spring.ai.token.cost.estimated.usd";

	/**
	 * Run after all other advisors so token counts are measured on the final, un-modified
	 * response.
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
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
		try {
			recordMetrics(chatClientResponse);
		}
		catch (TokenBudgetExceededException ex) {
			throw ex;
		}
		catch (Exception ex) {
			log.warn("[FinOps] Failed to record token-usage metrics -- continuing without metrics.", ex);
		}
		return chatClientResponse;
	}

	private void recordMetrics(ChatClientResponse chatClientResponse) {
		ChatResponse chatResponse = chatClientResponse.chatResponse();
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
		recordTokenCount(baseTags, "cached", usage.getCacheReadInputTokens());
		recordTokenCount(baseTags, "cache_write", usage.getCacheWriteInputTokens());

		recordCost(model, usage);
		checkBudget(usage);
	}

	private void recordTokenCount(Tags baseTags, String type, @Nullable Number count) {
		if (count == null || count.longValue() == 0) {
			return;
		}
		Counter.builder(METRIC_TOKEN_USAGE)
			.tags(baseTags)
			.tag("ai.token.type", type)
			.description("Cumulative LLM token usage by type")
			.register(this.meterRegistry)
			.increment(count.longValue());
	}

	private void recordCost(String model, Usage usage) {
		double promptPrice = this.properties.pricePerMillionPromptTokens();
		double completionPrice = this.properties.pricePerMillionCompletionTokens();
		if (promptPrice == 0.0 && completionPrice == 0.0) {
			return;
		}

		Tags costTags = Tags.of("model", model);
		if (usage.getPromptTokens() != null && promptPrice > 0.0) {
			double cost = (usage.getPromptTokens() / 1_000_000.0) * promptPrice;
			Counter.builder(METRIC_TOKEN_COST)
				.tags(costTags)
				.tag("tier", "prompt")
				.description("Estimated LLM cost in USD")
				.register(this.meterRegistry)
				.increment(cost);
		}
		if (usage.getCompletionTokens() != null && completionPrice > 0.0) {
			double cost = (usage.getCompletionTokens() / 1_000_000.0) * completionPrice;
			Counter.builder(METRIC_TOKEN_COST)
				.tags(costTags)
				.tag("tier", "completion")
				.description("Estimated LLM cost in USD")
				.register(this.meterRegistry)
				.increment(cost);
		}
	}

	private void checkBudget(Usage usage) {
		double threshold = this.properties.budgetThresholdUsd();
		if (threshold <= 0.0 || usage.getTotalTokens() == null) {
			return;
		}

		double cumulativeCost = this.meterRegistry.find(METRIC_TOKEN_COST)
			.counters()
			.stream()
			.mapToDouble(Counter::count)
			.sum();

		if (cumulativeCost > threshold) {
			log.warn("[FinOps] Token budget threshold of {} USD exceeded. Cumulative cost: {}", threshold,
					String.format("%.4f", cumulativeCost));
			throw new TokenBudgetExceededException(String.format(
					"LLM token budget of %.2f USD exceeded (current: %.4f). "
							+ "Increase spring.ai.finops.budget-threshold-usd to continue.",
					threshold, cumulativeCost));
		}
	}

	private static String resolveModelName(ChatResponse chatResponse) {
		try {
			String model = chatResponse.getMetadata().getModel();
			return (model != null && !model.isBlank()) ? model : "unknown";
		}
		catch (Exception ex) {
			return "unknown";
		}
	}

}
