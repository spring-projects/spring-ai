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

package org.springframework.ai.google.genai.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.ResponseStream;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class GoogleGenAiChatModelStreamingTests {

	@Mock
	private Client client;

	private GoogleGenAiChatModel chatModel;

	@Test
	void testStreamingWithNumbers() {
		// Setup
		setupChatModel();
		Models models = Mockito.mock(Models.class);
		ReflectionTestUtils.setField(this.client, "models", models);

		CountDownLatch latch = new CountDownLatch(1);

		List<GenerateContentResponse> chunks = new ArrayList<>();
		for (int i = 1; i <= 300; i++) {
			Part part = Part.builder().text(i + " ").build();
			Content content = Content.builder().parts(List.of(part)).build();
			Candidate candidate = Candidate.builder().content(content).build();
			GenerateContentResponse response = GenerateContentResponse.builder()
				.candidates(List.of(candidate))
				.modelVersion("v1")
				.build();
			chunks.add(response);
		}

		ResponseStream<GenerateContentResponse> iterable = Mockito.mock(ResponseStream.class);
		given(iterable.iterator()).willReturn(chunks.stream().map(r -> {
			int i = Integer
				.parseInt(r.candidates().get().get(0).content().get().parts().get().get(0).text().get().trim());
			if (i == 256) {
				latch.countDown();
			}
			return r;
		}).iterator());

		given(models.generateContentStream(any(String.class), any(List.class), any())).willReturn(iterable);

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
		ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
		ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
		this.chatModel = GoogleGenAiChatModel.builder()
			.genAiClient(this.client)
			.defaultOptions(GoogleGenAiChatOptions.builder().build())
			.toolCallingManager(toolCallingManager)
			.observationRegistry(ObservationRegistry.NOOP)
			.build();
	}

}
