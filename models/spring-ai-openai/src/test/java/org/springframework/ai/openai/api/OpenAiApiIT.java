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

package org.springframework.ai.openai.api;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Flux;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.Embedding;
import org.springframework.ai.openai.api.OpenAiApi.EmbeddingList;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiApiIT {

	OpenAiApi openAiApi = OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();

	@Test
	void chatCompletionEntity() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(
				new ChatCompletionRequest(List.of(chatCompletionMessage), "gpt-3.5-turbo", 0.8, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		Flux<ChatCompletionChunk> response = this.openAiApi.chatCompletionStream(
				new ChatCompletionRequest(List.of(chatCompletionMessage), "gpt-3.5-turbo", 0.8, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
	}

	@Test
	void validateReasoningTokens() {
		ChatCompletionMessage userMessage = new ChatCompletionMessage(
				"Are there an infinite number of prime numbers such that n mod 4 == 3? Think through the steps and respond.",
				ChatCompletionMessage.Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(userMessage), "gpt-5", null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, false, null, null, null, null,
				null, null, null, "high", null, null, null, null, null);
		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();

		OpenAiApi.Usage.CompletionTokenDetails completionTokenDetails = response.getBody()
			.usage()
			.completionTokenDetails();
		assertThat(completionTokenDetails).isNotNull();
		assertThat(completionTokenDetails.reasoningTokens()).isPositive();
	}

	@Test
	void embeddings() {
		ResponseEntity<EmbeddingList<Embedding>> response = this.openAiApi
			.embeddings(new OpenAiApi.EmbeddingRequest<String>("Hello world"));

		assertThat(response).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).embedding()).hasSize(1536);
	}

	@Test
	void inputAudio() throws IOException {
		var audioData = new ClassPathResource("speech/speech1.mp3").getContentAsByteArray();
		List<ChatCompletionMessage.MediaContent> content = List
			.of(new ChatCompletionMessage.MediaContent("What is this recording about?"),
					new ChatCompletionMessage.MediaContent(new ChatCompletionMessage.MediaContent.InputAudio(
							Base64.getEncoder().encodeToString(audioData),
							ChatCompletionMessage.MediaContent.InputAudio.Format.MP3)));
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(content, Role.USER);
		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(List.of(chatCompletionMessage),
				OpenAiApi.ChatModel.GPT_4_O_AUDIO_PREVIEW.getValue(), 0.0);
		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(chatCompletionRequest);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();

		assertThat(response.getBody().usage().promptTokensDetails().audioTokens()).isGreaterThan(0);
		assertThat(response.getBody().usage().completionTokenDetails().audioTokens()).isZero();

		assertThat(response.getBody().choices().get(0).message().content()).containsIgnoringCase("hobbits");
	}

	@Test
	void outputAudio() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Say 'I am a robot'", Role.USER);
		ChatCompletionRequest.AudioParameters audioParameters = new ChatCompletionRequest.AudioParameters(
				ChatCompletionRequest.AudioParameters.Voice.NOVA,
				ChatCompletionRequest.AudioParameters.AudioResponseFormat.MP3);
		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(List.of(chatCompletionMessage),
				OpenAiApi.ChatModel.GPT_4_O_AUDIO_PREVIEW.getValue(), audioParameters, false);
		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(chatCompletionRequest);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();

		assertThat(response.getBody().usage().promptTokensDetails().audioTokens()).isZero();
		assertThat(response.getBody().usage().completionTokenDetails().audioTokens()).isGreaterThan(0);

		assertThat(response.getBody().choices().get(0).message().audioOutput().data()).isNotNull();
		assertThat(response.getBody().choices().get(0).message().audioOutput().transcript())
			.containsIgnoringCase("robot");
	}

	@Test
	void streamOutputAudio() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(
				"What is the magic spell to make objects fly?", Role.USER);
		ChatCompletionRequest.AudioParameters audioParameters = new ChatCompletionRequest.AudioParameters(
				ChatCompletionRequest.AudioParameters.Voice.NOVA,
				ChatCompletionRequest.AudioParameters.AudioResponseFormat.MP3);
		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(List.of(chatCompletionMessage),
				OpenAiApi.ChatModel.GPT_4_O_AUDIO_PREVIEW.getValue(), audioParameters, true);

		Flux<ChatCompletionChunk> response = this.openAiApi.chatCompletionStream(chatCompletionRequest);

		assertThatThrownBy(response::blockLast).isInstanceOf(RuntimeException.class)
			.hasMessageContaining("400 Bad Request from POST https://api.openai.com/v1/chat/completions");
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "GPT_5", "GPT_5_CHAT_LATEST", "GPT_5_MINI", "GPT_5_NANO" })
	void chatCompletionEntityWithNewModels(OpenAiApi.ChatModel modelName) {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(
				new ChatCompletionRequest(List.of(chatCompletionMessage), modelName.getValue(), 1.0, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().choices()).isNotEmpty();
		assertThat(response.getBody().choices().get(0).message().content()).isNotEmpty();
		assertThat(response.getBody().model()).containsIgnoringCase(modelName.getValue());
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "GPT_5_NANO" })
	void chatCompletionEntityWithNewModelsAndLowVerbosity(OpenAiApi.ChatModel modelName) {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(
				"What is the answer to the ultimate question of life, the universe, and everything?", Role.USER);

		ChatCompletionRequest request = new ChatCompletionRequest(List.of(chatCompletionMessage), // messages
				modelName.getValue(), null, null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, false, null, 1.0, null, null, null, null, null, null, null, "low", null, null, null);

		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().choices()).isNotEmpty();
		assertThat(response.getBody().choices().get(0).message().content()).isNotEmpty();
		assertThat(response.getBody().model()).containsIgnoringCase(modelName.getValue());
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "GPT_5", "GPT_5_MINI", "GPT_5_NANO" })
	void chatCompletionEntityWithGpt5ModelsAndTemperatureShouldFail(OpenAiApi.ChatModel modelName) {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(chatCompletionMessage), modelName.getValue(),
				0.8);

		assertThatThrownBy(() -> this.openAiApi.chatCompletionEntity(request)).isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Unsupported value");
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "GPT_5_CHAT_LATEST" })
	void chatCompletionEntityWithGpt5ChatAndTemperatureShouldSucceed(OpenAiApi.ChatModel modelName) {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(chatCompletionMessage), modelName.getValue(),
				0.8);

		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().choices()).isNotEmpty();
		assertThat(response.getBody().choices().get(0).message().content()).isNotEmpty();
		assertThat(response.getBody().model()).containsIgnoringCase(modelName.getValue());
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "DEFAULT", "PRIORITY" })
	void chatCompletionEntityWithServiceTier(OpenAiApi.ServiceTier serviceTier) {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(
				"What is the answer to the ultimate question of life, the universe, and everything?", Role.USER);

		ChatCompletionRequest request = new ChatCompletionRequest(List.of(chatCompletionMessage), // messages
				OpenAiApi.ChatModel.GPT_4_O.value, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, serviceTier.getValue(), null, false, null, 1.0, null, null, null, null, null, null,
				null, null, null, null, null);

		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().serviceTier()).containsIgnoringCase(serviceTier.getValue());
	}

	@Test
	void userAgentHeaderIsSentInChatCompletionRequests() throws Exception {
		try (MockWebServer mockWebServer = new MockWebServer()) {
			mockWebServer.start();

			// Mock response from OpenAI
			mockWebServer.enqueue(new MockResponse().setResponseCode(200)
				.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setBody("""
						{
							"id": "chatcmpl-123",
							"object": "chat.completion",
							"created": 1677652288,
							"model": "gpt-3.5-turbo",
							"choices": [{
								"index": 0,
								"message": {
									"role": "assistant",
									"content": "Hello there!"
								},
								"finish_reason": "stop"
							}],
							"usage": {
								"prompt_tokens": 9,
								"completion_tokens": 2,
								"total_tokens": 11
							}
						}
						"""));

			// Create OpenAiApi instance pointing to mock server
			OpenAiApi testApi = OpenAiApi.builder()
				.apiKey(System.getenv("OPENAI_API_KEY"))
				.baseUrl(mockWebServer.url("/").toString())
				.build();

			// Make a request
			ChatCompletionMessage message = new ChatCompletionMessage("Hello world", Role.USER);
			ResponseEntity<ChatCompletion> response = testApi
				.chatCompletionEntity(new ChatCompletionRequest(List.of(message), "gpt-3.5-turbo", 0.8, false));

			// Verify the response succeeded
			assertThat(response).isNotNull();
			assertThat(response.getBody()).isNotNull();

			// Verify the User-Agent header was sent in the request
			RecordedRequest recordedRequest = mockWebServer.takeRequest();
			assertThat(recordedRequest.getHeader(OpenAiApi.HTTP_USER_AGENT_HEADER))
				.isEqualTo(OpenAiApi.SPRING_AI_USER_AGENT);

			mockWebServer.shutdown();
		}
	}

}
