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

package org.springframework.ai.openai.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class OpenAiChatModelStreamingTests {

	@Mock
	private OpenAiApi openAiApi;

	private OpenAiChatModel chatModel;

	@Test
	void testStreamingWithNumbers() {
		// Setup
		setupChatModel();

		CountDownLatch latch = new CountDownLatch(1);

		List<ChatCompletionChunk> chunks = new ArrayList<>();
		for (int i = 1; i <= 300; i++) {
			var choice = new ChunkChoice(null, 0, new ChatCompletionMessage(i + " ", Role.ASSISTANT), null);
			ChatCompletionChunk chunk = new ChatCompletionChunk("id", List.of(choice), 666L, "model", null, null,
					"chat.completion.chunk", null);
			chunks.add(chunk);
		}

		given(this.openAiApi.chatCompletionStream(isA(ChatCompletionRequest.class), any()))
			.willReturn(Flux.fromIterable(chunks).index().map(n -> {
				if (n.getT1() == 256) {
					latch.countDown();
				}
				return n.getT2();
			}));

		Flux<ChatResponse> result = this.chatModel.stream(new Prompt("Count to 300"));

		// @formatter:off
		List<ChatResponse> responses = result
			// Produce independently
			.subscribeOn(Schedulers.boundedElastic())
			// Fill the buffer of 256 items
			.publishOn(Schedulers.boundedElastic(), 256)
			.map(r -> {
				try {
					if (latch.await(5, TimeUnit.SECONDS)) {
						return r;
					}
					else {
						throw new RuntimeException("Timed out waiting for completion response");
					}
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
			})
			.collectList()
			.block();
		// @formatter:on
		assertThat(responses).isNotNull();
		assertThat(responses).isNotEmpty();

		StringBuilder fullContent = new StringBuilder();
		for (ChatResponse response : responses) {
			fullContent.append(response.getResult().getOutput().getText());
		}

		String expectedContent = IntStream.rangeClosed(1, 300).mapToObj(i -> i + " ").reduce("", String::concat);

		assertThat(fullContent.toString()).isEqualTo(expectedContent);
	}

	private void setupChatModel() {
		RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
		ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
		this.chatModel = new OpenAiChatModel(this.openAiApi, OpenAiChatOptions.builder().build(), toolCallingManager,
				retryTemplate, observationRegistry);
	}

}
