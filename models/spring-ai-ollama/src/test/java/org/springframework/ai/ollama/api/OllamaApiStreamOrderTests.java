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

package org.springframework.ai.ollama.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.ollama.api.OllamaApi.ChatRequest;
import org.springframework.ai.ollama.api.OllamaApi.ChatResponse;
import org.springframework.ai.ollama.api.OllamaApi.Message;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that streamed chunks are emitted in the order they were received.
 */
class OllamaApiStreamOrderTests {

	private static final int CHUNKS = 60;

	@Test
	void streamedChunksKeepOrderUnderSlowDemand() {
		StringBuilder ndjson = new StringBuilder();
		for (int i = 0; i < CHUNKS; i++) {
			ndjson.append("{\"model\":\"m\",\"created_at\":\"2024-01-01T00:00:00Z\",\"message\":"
					+ "{\"role\":\"assistant\",\"content\":\"t" + i + "\"},\"done\":false}\n");
		}
		ndjson.append("{\"model\":\"m\",\"created_at\":\"2024-01-01T00:00:00Z\",\"message\":"
				+ "{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\"}\n");

		WebClient.Builder webClientBuilder = WebClient.builder()
			.exchangeFunction(
					request -> Mono.just(ClientResponse.create(HttpStatus.OK, ExchangeStrategies.withDefaults())
						.header("Content-Type", MediaType.APPLICATION_NDJSON_VALUE)
						.body(ndjson.toString())
						.build()));
		OllamaApi api = OllamaApi.builder().baseUrl("http://localhost").webClientBuilder(webClientBuilder).build();
		ChatRequest request = ChatRequest.builder("m")
			.stream(true)
			.messages(List.of(Message.builder(Role.USER).content("hi").build()))
			.build();

		List<String> expected = IntStream.range(0, CHUNKS).mapToObj(i -> "t" + i).toList();
		for (int run = 0; run < 50; run++) {
			List<String> received = new ArrayList<>();
			Flux<ChatResponse> stream = api.streamingChat(request).publishOn(Schedulers.boundedElastic(), 1);
			stream.doOnNext(r -> received.add(r.message().content())).blockLast();
			received.removeIf(String::isEmpty);
			assertThat(received).as("run %d", run).containsExactlyElementsOf(expected);
		}
	}

}
