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

package org.springframework.ai.vertexai.gemini;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseStream;
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
import org.springframework.ai.model.tool.ToolCallingManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class VertexAiGeminiChatModelStreamingTests {

	@Mock
	private VertexAI vertexAi;

	private VertexAiGeminiChatModel chatModel;

	@Test
	void testStreamingWithNumbers() throws Exception {
		// Setup
		setupChatModel();
		VertexAiGeminiChatModel spyChatModel = Mockito.spy(this.chatModel);

		GenerativeModel generativeModel = Mockito.mock(GenerativeModel.class);
		VertexAiGeminiChatModel.GeminiRequest geminiRequest = new VertexAiGeminiChatModel.GeminiRequest(List.of(),
				generativeModel);
		Mockito.doReturn(geminiRequest).when(spyChatModel).createGeminiRequest(any());

		CountDownLatch latch = new CountDownLatch(1);

		List<GenerateContentResponse> chunks = new ArrayList<>();
		for (int i = 1; i <= 300; i++) {
			Part part = Part.newBuilder().setText(i + " ").build();
			Content content = Content.newBuilder().addParts(part).build();
			Candidate candidate = Candidate.newBuilder().setContent(content).build();
			GenerateContentResponse response = GenerateContentResponse.newBuilder().addCandidates(candidate).build();
			chunks.add(response);
		}

		ResponseStream<GenerateContentResponse> responseStream = Mockito.mock(ResponseStream.class);
		given(responseStream.stream()).willReturn(chunks.stream().map(r -> {
			int i = Integer.parseInt(r.getCandidates(0).getContent().getParts(0).getText().trim());
			if (i == 256) {
				latch.countDown();
			}
			return r;
		}));

		given(generativeModel.generateContentStream((List<Content>) any())).willReturn(responseStream);

		Flux<ChatResponse> result = spyChatModel.stream(new Prompt("Count to 300"));

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
		this.chatModel = VertexAiGeminiChatModel.builder()
			.vertexAI(this.vertexAi)
			.defaultOptions(
					VertexAiGeminiChatOptions.builder().model(VertexAiGeminiChatModel.ChatModel.GEMINI_1_5_PRO).build())
			.toolCallingManager(toolCallingManager)
			.observationRegistry(ObservationRegistry.NOOP)
			.build();
	}

}
