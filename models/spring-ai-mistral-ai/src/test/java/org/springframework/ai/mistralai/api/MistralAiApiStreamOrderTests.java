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

package org.springframework.ai.mistralai.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that streamed chunks are emitted in the order they were received.
 */
class MistralAiApiStreamOrderTests {

	private static final int CHUNKS = 60;

	@Test
	void streamedChunksKeepOrderUnderSlowDemand() {
		StringBuilder sse = new StringBuilder();
		for (int i = 0; i < CHUNKS; i++) {
			sse.append("data: {\"id\":\"1\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"m\","
					+ "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"t" + i
					+ "\"},\"finish_reason\":null}]}\n\n");
		}
		sse.append("data: [DONE]\n\n");

		WebClient.Builder webClientBuilder = WebClient.builder()
			.exchangeFunction(
					request -> Mono.just(ClientResponse.create(HttpStatus.OK, ExchangeStrategies.withDefaults())
						.header("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
						.body(sse.toString())
						.build()));
		MistralAiApi api = new MistralAiApi("http://localhost", "key", RestClient.builder(), webClientBuilder,
				RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(new ChatCompletionMessage("hi", Role.USER)),
				"m", 0.7, true);

		List<String> expected = IntStream.range(0, CHUNKS).mapToObj(i -> "t" + i).toList();
		for (int run = 0; run < 50; run++) {
			List<String> received = new ArrayList<>();
			Flux<ChatCompletionChunk> stream = api.chatCompletionStream(request)
				.publishOn(Schedulers.boundedElastic(), 1);
			stream.doOnNext(chunk -> received.add((String) chunk.choices().get(0).delta().content())).blockLast();
			assertThat(received).as("run %d", run).containsExactlyElementsOf(expected);
		}
	}

}
