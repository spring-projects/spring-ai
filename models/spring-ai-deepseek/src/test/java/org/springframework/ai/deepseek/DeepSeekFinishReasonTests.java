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

package org.springframework.ai.deepseek;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.publisher.SignalType;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests that the {@code finish_reason} values DeepSeek documents beyond {@code stop},
 * {@code length}, {@code content_filter} and {@code tool_calls}, namely
 * {@code insufficient_system_resource} and {@code aborted}, can be read by both
 * {@link DeepSeekChatModel#call} and {@link DeepSeekChatModel#stream}.
 */
class DeepSeekFinishReasonTests {

	private static final String USAGE = """
			"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}""";

	@ParameterizedTest
	@ValueSource(strings = { "insufficient_system_resource", "aborted" })
	void callReadsFinishReason(String finishReason) {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		server.expect(requestTo("https://api.deepseek.com/chat/completions"))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess("""
					{"id":"c1","object":"chat.completion","created":1,"model":"deepseek-chat",
					"choices":[{"index":0,"message":{"role":"assistant","content":"Partial"},
					"finish_reason":"%s"}],%s}""".formatted(finishReason, USAGE), MediaType.APPLICATION_JSON));
		DeepSeekChatModel chatModel = chatModel(
				DeepSeekApi.builder().apiKey("test").restClientBuilder(restClientBuilder).build());

		ChatResponse response = chatModel.call(new Prompt("Hi"));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("Partial");
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualToIgnoringCase(finishReason);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(12);
		server.verify();
	}

	@ParameterizedTest
	@ValueSource(strings = { "insufficient_system_resource", "aborted" })
	void streamReadsFinishReason(String finishReason) {
		String body = "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
				+ "\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,"
				+ "\"delta\":{\"role\":\"assistant\",\"content\":\"Partial\"},\"finish_reason\":null}]}\n\n"
				+ "data: {\"id\":\"c1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
				+ "\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\"},"
				+ "\"finish_reason\":\"" + finishReason + "\"}]," + USAGE + "}\n\n" + "data: [DONE]\n\n";
		WebClient.Builder webClientBuilder = WebClient.builder()
			.exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
				.header("Content-Type", "text/event-stream")
				.body(body)
				.build()));
		DeepSeekChatModel chatModel = chatModel(
				DeepSeekApi.builder().apiKey("test").webClientBuilder(webClientBuilder).build());

		// Signals rather than values, so that a failure shows how far the stream got
		List<Signal<ChatResponse>> signals = chatModel.stream(new Prompt("Hi"))
			.materialize()
			.collectList()
			.block(Duration.ofSeconds(10));

		assertThat(signals).extracting(Signal::getType)
			.as("signals: %s", signals)
			.containsExactly(SignalType.ON_NEXT, SignalType.ON_NEXT, SignalType.ON_COMPLETE);
		assertThat(signals.get(0).get().getResult().getOutput().getText()).isEqualTo("Partial");
		ChatResponse last = signals.get(1).get();
		assertThat(last.getResult().getMetadata().getFinishReason()).isEqualToIgnoringCase(finishReason);
		assertThat(last.getMetadata().getUsage().getTotalTokens()).isEqualTo(12);
	}

	private static DeepSeekChatModel chatModel(DeepSeekApi api) {
		return DeepSeekChatModel.builder()
			.deepSeekApi(api)
			.options(DeepSeekChatOptions.builder().model(DeepSeekApi.DEFAULT_CHAT_MODEL).build())
			.build();
	}

}
