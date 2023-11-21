/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.openai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.ai.client.AiResponse;
import org.springframework.ai.metadata.ChoiceMetadata;
import org.springframework.ai.metadata.GenerationMetadata;
import org.springframework.ai.metadata.PromptMetadata;
import org.springframework.ai.metadata.RateLimit;
import org.springframework.ai.metadata.Usage;
import org.springframework.ai.openai.OpenAiMockTestConfiguration;
import org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders;
import org.springframework.ai.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Tests using the {@link OpenAiClient} to send an {@literal OpenAI} API request (chat
 * completion) to test the presence of {@link GenerationMetadata} in the
 * {@link AiResponse}.
 *
 * @author John Blum
 * @since 0.7.0
 */
@SpringBootTest
@ContextConfiguration(classes = OpenAiClientWithGenerationMetadataTests.TestConfiguration.class)
@ActiveProfiles("spring-ai-openai-mocks")
@SuppressWarnings("unused")
class OpenAiClientWithGenerationMetadataTests {

	@Autowired
	private OpenAiClient aiClient;

	@Test
	void aiResponseContainsAiMetadata() {

		Prompt prompt = new Prompt("Reach for the sky.");

		AiResponse response = this.aiClient.generate(prompt);

		assertThat(response).isNotNull();

		GenerationMetadata generationMetadata = response.getGenerationMetadata();

		assertThat(generationMetadata).isNotNull();

		Usage usage = generationMetadata.getUsage();

		assertThat(usage).isNotNull();
		assertThat(usage.getPromptTokens()).isEqualTo(9L);
		assertThat(usage.getGenerationTokens()).isEqualTo(12L);
		assertThat(usage.getTotalTokens()).isEqualTo(21L);

		RateLimit rateLimit = generationMetadata.getRateLimit();

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

		PromptMetadata promptMetadata = response.getPromptMetadata();

		assertThat(promptMetadata).isNotNull();
		assertThat(promptMetadata).isEmpty();

		response.getGenerations().forEach(generation -> {
			ChoiceMetadata choiceMetadata = generation.getChoiceMetadata();
			assertThat(choiceMetadata).isNotNull();
			assertThat(choiceMetadata.getFinishReason()).isEqualTo("stop");
			assertThat(choiceMetadata.<Object>getContentFilterMetadata()).isNull();
		});
	}

	@SpringBootConfiguration
	@Import(OpenAiMockTestConfiguration.class)
	static class TestConfiguration {

		@Bean
		MockMvc mockMvc() {
			return MockMvcBuilders.standaloneSetup(new SpringOpenAiChatCompletionsController()).build();
		}

	}

	@RestController
	@RequestMapping("/spring-ai/api")
	@SuppressWarnings("all")
	static class SpringOpenAiChatCompletionsController {

		@PostMapping("/v1/chat/completions")
		ResponseEntity<?> chatCompletions(WebRequest request) {

			String json = getJson();

			ResponseEntity<?> response = ResponseEntity.status(HttpStatusCode.valueOf(200))
				.contentType(MediaType.APPLICATION_JSON)
				.contentLength(json.getBytes(StandardCharsets.UTF_8).length)
				.headers(httpHeaders -> {
					httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_LIMIT_HEADER.getName(), "4000");
					httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_REMAINING_HEADER.getName(), "999");
					httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_RESET_HEADER.getName(), "2d16h15m29s");
					httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_LIMIT_HEADER.getName(), "725000");
					httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_REMAINING_HEADER.getName(), "112358");
					httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_RESET_HEADER.getName(), "27h55s451ms");
				})
				.body(getJson());

			return response;
		}

		private String getJson() {
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

	}

}
