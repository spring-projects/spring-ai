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

package org.springframework.ai.deepseek.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionChunk;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link DeepSeekApi#chatCompletionStreamRaw} returns the chunks as sent and
 * {@link DeepSeekApi#chatCompletionStream} still merges the chunks of a tool call.
 *
 * @author Christian Tzolov
 * @author Dimitar Proynov
 */
class DeepSeekApiStreamTests {

	private static final String CHUNK_1 = "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
			+ "\"delta\":{\"role\":\"assistant\",\"content\":null,\"reasoning_content\":\"Think \","
			+ "\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\","
			+ "\"function\":{\"name\":\"getWeather\",\"arguments\":\"{\\\"city\\\":\"}}]},\"finish_reason\":null}]}\n\n";

	private static final String CHUNK_2 = "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
			+ "\"delta\":{\"content\":null,\"reasoning_content\":\"first.\","
			+ "\"tool_calls\":[{\"index\":0,\"id\":\"\",\"function\":{\"arguments\":\"\\\"Seoul\\\"}\"}}]},"
			+ "\"finish_reason\":null}]}\n\n";

	private static final String CHUNK_3 = "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,"
			+ "\"delta\":{\"content\":null},\"finish_reason\":\"tool_calls\"}]}\n\n";

	// A WebClient whose exchange function answers every request with the SSE body
	private final DeepSeekApi api = DeepSeekApi.builder()
		.apiKey("test")
		.webClientBuilder(WebClient.builder()
			.exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
				.header("Content-Type", "text/event-stream")
				.body(CHUNK_1 + CHUNK_2 + CHUNK_3 + "data: [DONE]\n\n")
				.build())))
		.build();

	@Test
	void rawStreamReturnsTheChunksAsSent() {
		List<ChatCompletionChunk> chunks = this.api.chatCompletionStreamRaw(request()).collectList().block();

		assertThat(chunks).hasSize(3);
		assertThat(chunks.get(0).choices().get(0).delta().toolCalls().get(0).function().arguments())
			.isEqualTo("{\"city\":");
		assertThat(chunks.get(1).choices().get(0).delta().toolCalls().get(0).id()).isEmpty();
		assertThat(chunks.get(2).choices().get(0).finishReason())
			.isEqualTo(DeepSeekApi.ChatCompletionFinishReason.TOOL_CALLS);
	}

	@Test
	void mergedStreamStillMergesTheToolCallWindow() {
		List<ChatCompletionChunk> chunks = this.api.chatCompletionStream(request()).collectList().block();

		assertThat(chunks).hasSize(1);
		ChatCompletionMessage delta = chunks.get(0).choices().get(0).delta();
		assertThat(delta.toolCalls()).hasSize(1);
		assertThat(delta.toolCalls().get(0).id()).isEqualTo("call_1");
		assertThat(delta.toolCalls().get(0).function().arguments()).isEqualTo("{\"city\":\"Seoul\"}");
		assertThat(delta.reasoningContent()).isEqualTo("Think first.");
		assertThat(chunks.get(0).choices().get(0).finishReason())
			.isEqualTo(DeepSeekApi.ChatCompletionFinishReason.TOOL_CALLS);
	}

	private static ChatCompletionRequest request() {
		return new ChatCompletionRequest(List.of(new ChatCompletionMessage("Weather?", Role.USER)), "deepseek-chat",
				0.7, true);
	}

}
