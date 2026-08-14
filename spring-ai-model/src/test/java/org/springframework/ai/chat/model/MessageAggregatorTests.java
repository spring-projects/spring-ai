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

package org.springframework.ai.chat.model;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.RateLimit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MessageAggregator}.
 *
 * @author Soby Chacko
 */
class MessageAggregatorTests {

	@Test
	void rateLimitFromStreamedChunkSurvivesAggregation() {
		RateLimit rateLimit = new TestRateLimit();

		Flux<ChatResponse> responses = Flux.just(chunk("Hello", new EmptyRateLimit()), chunk(" world", rateLimit));

		AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
		new MessageAggregator().aggregate(responses, aggregated::set).blockLast();

		assertThat(aggregated.get().getMetadata().getRateLimit()).isSameAs(rateLimit);
	}

	@Test
	void rateLimitStaysEmptyWhenNoChunkCarriesOne() {
		Flux<ChatResponse> responses = Flux.just(chunk("Hello", new EmptyRateLimit()),
				chunk(" world", new EmptyRateLimit()));

		AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
		new MessageAggregator().aggregate(responses, aggregated::set).blockLast();

		assertThat(aggregated.get().getMetadata().getRateLimit()).isInstanceOf(EmptyRateLimit.class);
	}

	private static ChatResponse chunk(String text, RateLimit rateLimit) {
		ChatResponseMetadata metadata = ChatResponseMetadata.builder().rateLimit(rateLimit).build();
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
	}

	private static final class TestRateLimit implements RateLimit {

		@Override
		public Long getRequestsLimit() {
			return 100L;
		}

		@Override
		public Long getRequestsRemaining() {
			return 99L;
		}

		@Override
		public Duration getRequestsReset() {
			return Duration.ofSeconds(1);
		}

		@Override
		public Long getTokensLimit() {
			return 1000L;
		}

		@Override
		public Long getTokensRemaining() {
			return 999L;
		}

		@Override
		public Duration getTokensReset() {
			return Duration.ofSeconds(2);
		}

	}

}
