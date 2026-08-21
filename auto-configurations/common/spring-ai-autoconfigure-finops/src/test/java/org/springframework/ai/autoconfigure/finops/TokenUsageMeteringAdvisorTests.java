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

import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link TokenUsageMeteringAdvisor}.
 */
class TokenUsageMeteringAdvisorTests {

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

	private final ChatClientRequest request = ChatClientRequest.builder()
		.prompt(new Prompt(new UserMessage("hi")))
		.build();

	@Test
	void recordsPromptCompletionAndTotalTokenCounters() {
		FinOpsProperties properties = new FinOpsProperties(true, 0.0, 0.0, 0.0);
		TokenUsageMeteringAdvisor advisor = new TokenUsageMeteringAdvisor(this.meterRegistry, properties);
		CallAdvisorChain chain = chainReturning(chatClientResponse("gpt-4o", 10, 5, null, null));

		advisor.adviseCall(this.request, chain);

		assertThat(this.meterRegistry
			.counter(TokenUsageMeteringAdvisor.METRIC_TOKEN_USAGE, "model", "gpt-4o", "operation", "chat",
					"ai.token.type", "prompt")
			.count()).isEqualTo(10.0);
		assertThat(this.meterRegistry
			.counter(TokenUsageMeteringAdvisor.METRIC_TOKEN_USAGE, "model", "gpt-4o", "operation", "chat",
					"ai.token.type", "completion")
			.count()).isEqualTo(5.0);
		assertThat(this.meterRegistry
			.counter(TokenUsageMeteringAdvisor.METRIC_TOKEN_USAGE, "model", "gpt-4o", "operation", "chat",
					"ai.token.type", "total")
			.count()).isEqualTo(15.0);
	}

	@Test
	void recordsCachedAndCacheWriteTokenCountersFromUsage() {
		FinOpsProperties properties = new FinOpsProperties(true, 0.0, 0.0, 0.0);
		TokenUsageMeteringAdvisor advisor = new TokenUsageMeteringAdvisor(this.meterRegistry, properties);
		CallAdvisorChain chain = chainReturning(chatClientResponse("claude-3", 100, 20, 40L, 15L));

		advisor.adviseCall(this.request, chain);

		assertThat(this.meterRegistry
			.counter(TokenUsageMeteringAdvisor.METRIC_TOKEN_USAGE, "model", "claude-3", "operation", "chat",
					"ai.token.type", "cached")
			.count()).isEqualTo(40.0);
		assertThat(this.meterRegistry
			.counter(TokenUsageMeteringAdvisor.METRIC_TOKEN_USAGE, "model", "claude-3", "operation", "chat",
					"ai.token.type", "cache_write")
			.count()).isEqualTo(15.0);
	}

	@Test
	void doesNotRecordCacheCountersWhenUsageReportsNone() {
		FinOpsProperties properties = new FinOpsProperties(true, 0.0, 0.0, 0.0);
		TokenUsageMeteringAdvisor advisor = new TokenUsageMeteringAdvisor(this.meterRegistry, properties);
		CallAdvisorChain chain = chainReturning(chatClientResponse("gpt-4o", 10, 5, null, null));

		advisor.adviseCall(this.request, chain);

		assertThat(this.meterRegistry.find(TokenUsageMeteringAdvisor.METRIC_TOKEN_USAGE)
			.tag("ai.token.type", "cached")
			.counter()).isNull();
	}

	@Test
	void recordsEstimatedCostWhenPricingConfigured() {
		FinOpsProperties properties = new FinOpsProperties(true, 3.00, 15.00, 0.0);
		TokenUsageMeteringAdvisor advisor = new TokenUsageMeteringAdvisor(this.meterRegistry, properties);
		CallAdvisorChain chain = chainReturning(chatClientResponse("gpt-4o", 1_000_000, 1_000_000, null, null));

		advisor.adviseCall(this.request, chain);

		assertThat(this.meterRegistry
			.counter(TokenUsageMeteringAdvisor.METRIC_TOKEN_COST, "model", "gpt-4o", "tier", "prompt")
			.count()).isEqualTo(3.00);
		assertThat(this.meterRegistry
			.counter(TokenUsageMeteringAdvisor.METRIC_TOKEN_COST, "model", "gpt-4o", "tier", "completion")
			.count()).isEqualTo(15.00);
	}

	@Test
	void doesNotRecordCostWhenPricingNotConfigured() {
		FinOpsProperties properties = new FinOpsProperties(true, 0.0, 0.0, 0.0);
		TokenUsageMeteringAdvisor advisor = new TokenUsageMeteringAdvisor(this.meterRegistry, properties);
		CallAdvisorChain chain = chainReturning(chatClientResponse("gpt-4o", 10, 5, null, null));

		advisor.adviseCall(this.request, chain);

		assertThat(this.meterRegistry.find(TokenUsageMeteringAdvisor.METRIC_TOKEN_COST).counters()).isEmpty();
	}

	@Test
	void throwsTokenBudgetExceededExceptionWhenThresholdCrossed() {
		FinOpsProperties properties = new FinOpsProperties(true, 3.00, 15.00, 0.01);
		TokenUsageMeteringAdvisor advisor = new TokenUsageMeteringAdvisor(this.meterRegistry, properties);
		CallAdvisorChain chain = chainReturning(chatClientResponse("gpt-4o", 1_000_000, 1_000_000, null, null));

		assertThatThrownBy(() -> advisor.adviseCall(this.request, chain))
			.isInstanceOf(TokenBudgetExceededException.class)
			.hasMessageContaining("budget");
	}

	@Test
	void doesNotThrowWhenBudgetThresholdIsZero() {
		FinOpsProperties properties = new FinOpsProperties(true, 3.00, 15.00, 0.0);
		TokenUsageMeteringAdvisor advisor = new TokenUsageMeteringAdvisor(this.meterRegistry, properties);
		CallAdvisorChain chain = chainReturning(chatClientResponse("gpt-4o", 1_000_000, 1_000_000, null, null));

		ChatClientResponse response = advisor.adviseCall(this.request, chain);

		assertThat(response).isNotNull();
	}

	@Test
	void returnsResponseUnmodifiedWhenChatResponseIsNull() {
		FinOpsProperties properties = new FinOpsProperties(true, 0.0, 0.0, 0.0);
		TokenUsageMeteringAdvisor advisor = new TokenUsageMeteringAdvisor(this.meterRegistry, properties);
		ChatClientResponse emptyResponse = ChatClientResponse.builder().build();
		CallAdvisorChain chain = chainReturning(emptyResponse);

		ChatClientResponse result = advisor.adviseCall(this.request, chain);

		assertThat(result).isSameAs(emptyResponse);
		assertThat(this.meterRegistry.getMeters()).isEmpty();
	}

	@Test
	void advisorRunsAtLowestPrecedenceAndHasStableName() {
		TokenUsageMeteringAdvisor advisor = new TokenUsageMeteringAdvisor(this.meterRegistry,
				new FinOpsProperties(true, 0.0, 0.0, 0.0));

		assertThat(advisor.getName()).isEqualTo("TokenUsageMeteringAdvisor");
		assertThat(advisor.getOrder()).isEqualTo(org.springframework.core.Ordered.LOWEST_PRECEDENCE);
	}

	private static CallAdvisorChain chainReturning(ChatClientResponse response) {
		CallAdvisorChain chain = mock(CallAdvisorChain.class);
		given(chain.nextCall(org.mockito.ArgumentMatchers.any())).willReturn(response);
		return chain;
	}

	private static ChatClientResponse chatClientResponse(String model, Integer promptTokens, Integer completionTokens,
			Long cacheReadInputTokens, Long cacheWriteInputTokens) {
		DefaultUsage usage = new DefaultUsage(promptTokens, completionTokens, promptTokens + completionTokens, null,
				cacheReadInputTokens, cacheWriteInputTokens);
		ChatResponseMetadata metadata = ChatResponseMetadata.builder().model(model).usage(usage).build();
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("hello"))), metadata);
		return ChatClientResponse.builder().chatResponse(chatResponse).context(Map.of()).build();
	}

}
