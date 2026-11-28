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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests that {@link DeepSeekApi} reads the DeepSeek-specific finish reasons
 * {@code insufficient_system_resource} and {@code aborted} in both the blocking and the
 * streaming API.
 *
 * @author Yu Xia
 */
class DeepSeekApiFinishReasonTests {

	private static final String COMPLETION_INSUFFICIENT_RESOURCE = """
			{"id":"a1","object":"chat.completion","created":1786656314,"model":"deepseek-chat",
			"choices":[{"index":0,"message":{"role":"assistant","content":"Partial"},"finish_reason":"insufficient_system_resource"}],
			"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}""";

	private static final String CHUNK_PARTIAL = "data: {\"id\":\"s1\",\"object\":\"chat.completion.chunk\","
			+ "\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Partial\"},"
			+ "\"finish_reason\":null}]}\n\n";

	private static final String CHUNK_ABORTED = "data: {\"id\":\"s1\",\"object\":\"chat.completion.chunk\","
			+ "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\"},\"finish_reason\":\"aborted\"}],"
			+ "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12}}\n\n";

	@Test
	void chatCompletionEntityReadsInsufficientSystemResource() {
		DeepSeekApi api = apiWithBody("application/json", COMPLETION_INSUFFICIENT_RESOURCE);
		DeepSeekApi.ChatCompletion completion = api.chatCompletionEntity(request(false)).getBody();
		assertThat(completion.choices().get(0).finishReason().name()).isEqualTo("INSUFFICIENT_SYSTEM_RESOURCE");
	}

	@Test
	void streamCarriesAbortedFinishReasonAndItsUsage() {
		DeepSeekApi api = apiWithBody("text/event-stream", CHUNK_PARTIAL + CHUNK_ABORTED + "data: [DONE]\n\n");
		List<ChatCompletionChunk> chunks = api.chatCompletionStream(request(true)).collectList().block();
		assertThat(chunks).hasSize(2);
		assertThat(chunks.get(1).choices().get(0).finishReason().name()).isEqualTo("ABORTED");
		assertThat(chunks.get(1).usage().totalTokens()).isEqualTo(12);
	}

	private static DeepSeekApi apiWithBody(String contentType, String body) {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer restServer = MockRestServiceServer.bindTo(restClientBuilder).build();
		restServer.expect(requestTo("https://api.deepseek.com/chat/completions"))
			.andRespond(withSuccess(body, MediaType.parseMediaType(contentType)));
		return DeepSeekApi.builder()
			.apiKey("test")
			.restClientBuilder(restClientBuilder)
			.webClientBuilder(WebClient.builder()
				.exchangeFunction(request -> Mono
					.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", contentType).body(body).build())))
			.build();
	}

	private static ChatCompletionRequest request(boolean stream) {
		return new ChatCompletionRequest(List.of(new ChatCompletionMessage("Weather?", Role.USER)), stream);
	}

}
