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

package org.springframework.ai.bedrock.converse.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;

import org.springframework.ai.chat.model.ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ConverseChatResponseStream}.
 *
 * @author Jewoo Shin
 */
class ConverseChatResponseStreamTests {

	private static final int CHUNK_COUNT = 600;

	@Test
	void preservesAllChunksWhenDownstreamConsumerIsSlow() throws Exception {
		BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient = mock(BedrockRuntimeAsyncClient.class);
		when(bedrockRuntimeAsyncClient.converseStream(any(ConverseStreamRequest.class), any()))
			.thenReturn(CompletableFuture.completedFuture(null));

		ConverseChatResponseStream responseStream = new ConverseChatResponseStream(bedrockRuntimeAsyncClient,
				ConverseStreamRequest.builder().modelId("test-model").build(), null);

		// Block the first downstream response so publishOn stops draining its prefetch
		// queue while the producer emits the remaining chunks.
		CountDownLatch firstDownstreamResponse = new CountDownLatch(1);
		CountDownLatch releaseDownstream = new CountDownLatch(1);
		CompletableFuture<List<ChatResponse>> responses = responseStream.stream()
			.publishOn(Schedulers.boundedElastic(), 256)
			.map(response -> {
				firstDownstreamResponse.countDown();
				await(releaseDownstream);
				return response;
			})
			.take(CHUNK_COUNT)
			.collectList()
			.toFuture();

		responseStream.visitContentBlockDelta(textDelta(0));
		assertThat(firstDownstreamResponse.await(5, TimeUnit.SECONDS)).isTrue();

		try {
			for (int i = 1; i < CHUNK_COUNT; i++) {
				responseStream.visitContentBlockDelta(textDelta(i));
			}
		}
		finally {
			releaseDownstream.countDown();
		}

		assertThat(responses.get(10, TimeUnit.SECONDS))
			.extracting(response -> response.getResult().getOutput().getText())
			.containsExactly(IntStream.range(0, CHUNK_COUNT).mapToObj(i -> i + " ").toArray(String[]::new));
	}

	private ContentBlockDeltaEvent textDelta(int index) {
		return ContentBlockDeltaEvent.builder()
			.contentBlockIndex(0)
			.delta(ContentBlockDelta.builder().text(index + " ").build())
			.build();
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out waiting for downstream consumer");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

}
