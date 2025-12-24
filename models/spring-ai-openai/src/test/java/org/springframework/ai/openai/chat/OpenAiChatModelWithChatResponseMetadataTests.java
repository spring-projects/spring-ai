/*
 * Copyright 2023-2024 the original author or authors.
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

import java.time.Duration;

import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @author John Blum
 * @author Christian Tzolov
 * @since 0.7.0
 */
@RestClientTest(OpenAiChatModelWithChatResponseMetadataTests.Config.class)
public class OpenAiChatModelWithChatResponseMetadataTests {

	private static String TEST_API_KEY = "sk-1234567890";

	@Autowired
	private OpenAiChatModel openAiChatClient;

	@Autowired
	private MockRestServiceServer server;

	@AfterEach
	void resetMockServer() {
		this.server.reset();
	}

	@Test
	void aiResponseContainsAiMetadata() {

		prepareMock(false);

		Prompt prompt = new Prompt("Reach for the sky.");

		ChatResponse response = this.openAiChatClient.call(prompt);

		assertThat(response).isNotNull();

		ChatResponseMetadata chatResponseMetadata = response.getMetadata();

		assertThat(chatResponseMetadata).isNotNull();

		Usage usage = chatResponseMetadata.getUsage();

		assertThat(usage).isNotNull();
		assertThat(usage.getPromptTokens()).isEqualTo(9L);
		assertThat(usage.getCompletionTokens()).isEqualTo(12L);
		assertThat(usage.getTotalTokens()).isEqualTo(21L);

		RateLimit rateLimit = chatResponseMetadata.getRateLimit();

		Duration expectedRequestsReset = Duration.ofDays(2L)
			.plus(Duration.ofHours(16L))
			.plus(Duration.ofMinutes(15))
			.plus(Duration.ofSeconds(29L));

		Duration expectedTokensReset = Duration.ofHours(27L)
			.plus(Duration.ofSeconds(55L))
			.plus(Duration.ofMillis(451L));

		assertThat(rateLimit).isNotNull();
		assertThat(rateLimit.getRequestsLimit()).isEqualTo(4000L);
		assertThat(rateLimit.getRequestsRemaining()).isEqualTo(999);
		assertThat(rateLimit.getRequestsReset()).isEqualTo(expectedRequestsReset);
		assertThat(rateLimit.getTokensLimit()).isEqualTo(725_000L);
		assertThat(rateLimit.getTokensRemaining()).isEqualTo(112_358L);
		assertThat(rateLimit.getTokensReset()).isEqualTo(expectedTokensReset);

		PromptMetadata promptMetadata = response.getMetadata().getPromptMetadata();

		assertThat(promptMetadata).isNotNull();
		assertThat(promptMetadata).isEmpty();

		response.getResults().forEach(generation -> {
			ChatGenerationMetadata chatGenerationMetadata = generation.getMetadata();
			var logprobs = chatGenerationMetadata.get("logprobs");
			assertThat(logprobs).isNull();
			assertThat(chatGenerationMetadata).isNotNull();
			assertThat(chatGenerationMetadata.getFinishReason()).isEqualTo("STOP");
			assertThat(chatGenerationMetadata.getContentFilters()).isEmpty();
		});
	}

	@Test
	void aiResponseContainsAiLogprobsMetadata() {

		prepareMock(true);

		Prompt prompt = new Prompt("Reach for the sky.", new OpenAiChatOptions.Builder().logprobs(true).build());

		ChatResponse response = this.openAiChatClient.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getMetadata()).isNotNull();

		var logprobs = response.getResult().getMetadata().get("logprobs");
		assertThat(logprobs).isNotNull().isInstanceOf(OpenAiApi.LogProbs.class);
	}

	private void prepareMock(boolean includeLogprobs) {

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_LIMIT_HEADER.getName(), "4000");
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_REMAINING_HEADER.getName(), "999");
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_RESET_HEADER.getName(), "2d16h15m29s");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_LIMIT_HEADER.getName(), "725000");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_REMAINING_HEADER.getName(), "112358");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_RESET_HEADER.getName(), "27h55s451ms");

		this.server.expect(requestTo(StringContains.containsString("/v1/chat/completions")))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_KEY))
			.andRespond(withSuccess(getJson(includeLogprobs), MediaType.APPLICATION_JSON).headers(httpHeaders));

	}

	private String getBaseJson() {
		return """
				   {
				      "id": "chatcmpl-123",
				      "object": "chat.completion",
				      "created": 1677652288,
				      "model": "gpt-3.5-turbo-0613",
				      "choices": [{
				      "index": 0,
				      "message": {
				         "role": "assistant",
				         "content": "I surrender!"
				      },
				      %s
				      "finish_reason": "stop"
				      }],
				      "usage": {
				      "prompt_tokens": 9,
				      "completion_tokens": 12,
				      "total_tokens": 21
				      }
				   }
				""";
	}

	private String getJson(boolean includeLogprobs) {
		if (includeLogprobs) {
			String logprobs = """
					    "logprobs" : {
					         "content" : [ {
					           "token" : "I",
					           "logprob" : -0.029507114,
					           "bytes" : [ 73 ],
					           "top_logprobs" : [ ]
					         }, {
					           "token" : " surrender!",
					           "logprob" : -0.061970375,
					           "bytes" : [ 32, 115, 117, 114, 114, 101, 110, 100, 101, 114, 33 ],
					           "top_logprobs" : [ ]
					         } ]
					       },
					""";
			return String.format(getBaseJson(), logprobs);
		}

		return String.format(getBaseJson(), "");
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi(RestClient.Builder builder) {
			return OpenAiApi.builder()
				.apiKey(TEST_API_KEY)
				.restClientBuilder(builder)
				.webClientBuilder(WebClient.builder())
				.build();
		}

		@Bean
		public OpenAiChatModel openAiClient(OpenAiApi openAiApi) {
			return OpenAiChatModel.builder().openAiApi(openAiApi).build();
		}

	}

}
