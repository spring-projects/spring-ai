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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekApiStreamingTests {

	@Test
	void preservesChunkOrderWhenRequestedOneAtATime() throws Exception {
		List<String> expected = IntStream.range(0, 60).mapToObj(Integer::toString).toList();
		Flux<DataBuffer> body = Flux.fromIterable(expected)
			.map(number -> "data: {\"id\":\"test\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"" + number
					+ "\"}}]}\n\n")
			.concatWithValues("data: [DONE]\n\n")
			.map(event -> DefaultDataBufferFactory.sharedInstance.wrap(event.getBytes(StandardCharsets.UTF_8)));
		WebClient webClient = WebClient.builder()
			.clientConnector((method, uri, callback) -> Mono.error(new AssertionError("Unexpected HTTP connection")))
			.exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
				.body(body)
				.build()))
			.build();
		DeepSeekApi api = new DeepSeekApi("/chat/completions", "/beta",
				RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()).build(), webClient);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(), "deepseek-flash", 0.0, true);
		List<String> received = new ArrayList<>();
		CompletableFuture<Void> completion = new CompletableFuture<>();
		BaseSubscriber<String> subscriber = new BaseSubscriber<>() {
			@Override
			protected void hookOnSubscribe(Subscription subscription) {
				request(1);
			}

			@Override
			protected void hookOnNext(String chunk) {
				received.add(chunk);
			}

			@Override
			protected void hookOnComplete() {
				completion.complete(null);
			}

			@Override
			protected void hookOnError(Throwable error) {
				completion.completeExceptionally(error);
			}
		};

		// Let the synchronous source emit while downstream demand is limited.
		api.chatCompletionStream(request).map(chunk -> chunk.choices().get(0).delta().content()).subscribe(subscriber);
		try {
			for (int i = 1; i < expected.size(); i++) {
				subscriber.request(1);
			}
			completion.get(5, TimeUnit.SECONDS);
			assertThat(received).containsExactlyElementsOf(expected);
		}
		finally {
			subscriber.cancel();
		}
	}

}
