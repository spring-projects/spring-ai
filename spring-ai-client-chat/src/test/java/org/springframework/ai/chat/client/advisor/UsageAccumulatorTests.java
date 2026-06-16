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

package org.springframework.ai.chat.client.advisor;

import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UsageAccumulator}.
 *
 * @author Jewoo Shin
 */
class UsageAccumulatorTests {

	@Test
	void totalIsNullBeforeAnyAdd() {
		assertThat(new UsageAccumulator().total()).isNull();
	}

	@Test
	void addFoldsUsageAcrossRounds() {
		UsageAccumulator accumulator = new UsageAccumulator();

		accumulator.add(responseWith(new DefaultUsage(10, 20, 30)));
		ChatResponse total = accumulator.add(responseWith(new DefaultUsage(1, 2, 3)));

		assertThat(total).isNotNull();
		assertUsage(total.getMetadata().getUsage(), 11, 22, 33);
		assertThat(accumulator.total()).isSameAs(total);
	}

	@Test
	void applyTotalUsageStampsAccumulatedTotalOntoResponse() {
		UsageAccumulator accumulator = new UsageAccumulator();
		accumulator.add(responseWith(new DefaultUsage(10, 20, 30)));
		accumulator.add(responseWith(new DefaultUsage(1, 2, 3)));

		ChatClientResponse finalResponse = clientResponseWith(new DefaultUsage(1, 2, 3));
		ChatClientResponse result = accumulator.applyTotalUsage(finalResponse);

		assertUsage(result.chatResponse().getMetadata().getUsage(), 11, 22, 33);
	}

	@Test
	void applyTotalUsageLeavesResponseUnchangedWhenNothingAccumulated() {
		ChatClientResponse response = clientResponseWith(new DefaultUsage(1, 2, 3));

		ChatClientResponse result = new UsageAccumulator().applyTotalUsage(response);

		assertThat(result).isSameAs(response);
	}

	@Test
	void applyPreviousTotalToChunkAddsPreviousTotalToUsageBearingChunk() {
		ChatResponse previousTotal = responseWith(new DefaultUsage(10, 20, 30));
		ChatClientResponse chunk = clientResponseWith(new DefaultUsage(1, 2, 3));

		ChatClientResponse result = UsageAccumulator.applyPreviousTotalToChunk(chunk, previousTotal);

		assertUsage(result.chatResponse().getMetadata().getUsage(), 11, 22, 33);
	}

	@Test
	void applyPreviousTotalToChunkLeavesUsageFreeChunkUnchanged() {
		ChatResponse previousTotal = responseWith(new DefaultUsage(10, 20, 30));
		ChatClientResponse chunk = clientResponseWith(new DefaultUsage(0, 0, 0));

		ChatClientResponse result = UsageAccumulator.applyPreviousTotalToChunk(chunk, previousTotal);

		assertThat(result).isSameAs(chunk);
	}

	@Test
	void usageCorrectionEmitsUsageOnlyResponseWhenFinalRoundHasNoUsage() {
		ChatClientResponse aggregated = clientResponseWith(new DefaultUsage(0, 0, 0));
		ChatResponse round = responseWith(new DefaultUsage(0, 0, 0));
		ChatResponse accumulated = responseWith(new DefaultUsage(10, 20, 30));

		List<ChatClientResponse> result = UsageAccumulator.usageCorrection(aggregated, round, accumulated)
			.collectList()
			.block();

		assertThat(result).isNotNull().hasSize(1);
		assertUsage(result.get(0).chatResponse().getMetadata().getUsage(), 10, 20, 30);
		// Usage-only: no content generations are duplicated.
		assertThat(result.get(0).chatResponse().getResults()).isEmpty();
	}

	@Test
	void usageCorrectionEmitsNothingWhenFinalRoundReportsUsage() {
		ChatClientResponse aggregated = clientResponseWith(new DefaultUsage(1, 2, 3));
		ChatResponse round = responseWith(new DefaultUsage(1, 2, 3));
		ChatResponse accumulated = responseWith(new DefaultUsage(11, 22, 33));

		Flux<ChatClientResponse> result = UsageAccumulator.usageCorrection(aggregated, round, accumulated);

		assertThat(result.collectList().block()).isEmpty();
	}

	@Test
	void usageCorrectionEmitsNothingWhenNothingAccumulated() {
		ChatClientResponse aggregated = clientResponseWith(new DefaultUsage(0, 0, 0));
		ChatResponse round = responseWith(new DefaultUsage(0, 0, 0));

		Flux<ChatClientResponse> result = UsageAccumulator.usageCorrection(aggregated, round, null);

		assertThat(result.collectList().block()).isEmpty();
	}

	private ChatResponse responseWith(Usage usage) {
		ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(usage).build();
		return ChatResponse.builder()
			.generations(List.of(new Generation(new AssistantMessage("response"))))
			.metadata(metadata)
			.build();
	}

	private ChatClientResponse clientResponseWith(Usage usage) {
		return ChatClientResponse.builder().chatResponse(responseWith(usage)).build();
	}

	private void assertUsage(Usage usage, int promptTokens, int completionTokens, int totalTokens) {
		assertThat(usage.getPromptTokens()).isEqualTo(promptTokens);
		assertThat(usage.getCompletionTokens()).isEqualTo(completionTokens);
		assertThat(usage.getTotalTokens()).isEqualTo(totalTokens);
	}

}
