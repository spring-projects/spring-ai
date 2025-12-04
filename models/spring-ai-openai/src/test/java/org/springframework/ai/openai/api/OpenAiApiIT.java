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

	// Responses API Tests

	@Test
	void responseEntity() {
		// Create a simple response request
		OpenAiApi.ResponseRequest request = new OpenAiApi.ResponseRequest("Say hello in one sentence", "gpt-4o");

		ResponseEntity<OpenAiApi.Response> response = this.openAiApi.responseEntity(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().id()).isNotNull();
		assertThat(response.getBody().object()).isEqualTo("response");
		assertThat(response.getBody().status()).isEqualTo("completed");
		assertThat(response.getBody().model()).contains("gpt-4o");
		assertThat(response.getBody().output()).isNotEmpty();

		// Verify output contains a message with text content
		OpenAiApi.Response.OutputItem firstOutput = response.getBody().output().get(0);
		assertThat(firstOutput).isNotNull();
		assertThat(firstOutput.content()).isNotEmpty();

		// Find and verify text content
		boolean hasTextContent = firstOutput.content()
			.stream()
			.anyMatch(content -> "output_text".equals(content.type()) && content.text() != null
					&& !content.text().isEmpty());
		assertThat(hasTextContent).isTrue();

		// Verify usage information
		assertThat(response.getBody().usage()).isNotNull();
		assertThat(response.getBody().usage().totalTokens()).isPositive();
	}

	@Test
	void responseStream() {
		// Create a streaming response request
		OpenAiApi.ResponseRequest request = new OpenAiApi.ResponseRequest("Count from 1 to 3", "gpt-4o", true);

		Flux<OpenAiApi.ResponseStreamEvent> eventStream = this.openAiApi.responseStream(request);

		assertThat(eventStream).isNotNull();

		List<OpenAiApi.ResponseStreamEvent> events = eventStream.collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).isNotEmpty();

		// Verify we received the expected event types
		boolean hasCreatedEvent = events.stream().anyMatch(e -> "response.created".equals(e.type()));
		boolean hasOutputEvent = events.stream()
			.anyMatch(e -> e.type() != null && e.type().contains("output") || e.type().contains("delta"));
		boolean hasCompletedEvent = events.stream()
			.anyMatch(e -> e.type() != null && e.type().contains("completed") || e.type().contains("done"));

		assertThat(hasCreatedEvent || hasOutputEvent || hasCompletedEvent).isTrue();

		// Verify at least some events have sequence numbers
		boolean hasSequenceNumbers = events.stream().anyMatch(e -> e.sequenceNumber() != null);
		assertThat(hasSequenceNumbers).isTrue();
	}

	@Test
	void responseWithInstructionsAndConfiguration() {
		// Create a request with custom configuration
		OpenAiApi.ResponseRequest request = new OpenAiApi.ResponseRequest("gpt-4o", // model
				"What is 2+2?", // input
				"You are a helpful math tutor", // instructions
				100, // maxOutputTokens
				null, // maxToolCalls
				0.7, // temperature
				null, // topP
				null, // tools
				null, // toolChoice
				null, // parallelToolCalls
				false, // stream
				true, // store
				null, // metadata
				null, // conversation
				null, // previousResponseId
				null, // text
				null, // reasoning
				null, // include
				null, // truncation
				null, // serviceTier
				null, // promptCacheKey
				null, // promptCacheRetention
				null, // safetyIdentifier
				null // background
		);

		ResponseEntity<OpenAiApi.Response> response = this.openAiApi.responseEntity(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo("completed");
		assertThat(response.getBody().temperature()).isEqualTo(0.7);
		assertThat(response.getBody().store()).isTrue();

		// Verify the response contains an answer
		String outputText = response.getBody()
			.output()
			.stream()
			.filter(item -> "message".equals(item.type()))
			.flatMap(item -> item.content().stream())
			.filter(content -> "output_text".equals(content.type()))
			.map(OpenAiApi.Response.ContentItem::text)
			.findFirst()
			.orElse(null);

		assertThat(outputText).isNotNull();
		assertThat(outputText).containsAnyOf("4", "four");
	}

	@Test
	void responseWithWebSearchTool() {
		// Create a web_search tool configuration
		// The web_search tool allows the model to search the internet for current
		// information
		var webSearchTool = java.util.Map.of("type", "web_search");

		// Create a request that requires current information from the web
		OpenAiApi.ResponseRequest request = new OpenAiApi.ResponseRequest("gpt-4o", // model
				"What is the current weather in San Francisco?", // input - requires web
																	// search
				null, // instructions
				null, // maxOutputTokens
				null, // maxToolCalls
				null, // temperature
				null, // topP
				List.of(webSearchTool), // tools - enable web_search
				null, // toolChoice
				null, // parallelToolCalls
				false, // stream
				null, // store
				null, // metadata
				null, // conversation
				null, // previousResponseId
				null, // text
				null, // reasoning
				List.of("web_search_call.action.sources"), // include - get search sources
				null, // truncation
				null, // serviceTier
				null, // promptCacheKey
				null, // promptCacheRetention
				null, // safetyIdentifier
				null // background
		);

		ResponseEntity<OpenAiApi.Response> response = this.openAiApi.responseEntity(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo("completed");
		assertThat(response.getBody().output()).isNotEmpty();

		// Verify that web_search tool was called
		boolean hasWebSearchCall = response.getBody()
			.output()
			.stream()
			.anyMatch(item -> "web_search_call".equals(item.type()));

		assertThat(hasWebSearchCall).as("Response should contain a web_search_call output item").isTrue();

		// Verify the final response contains information (likely from web search)
		boolean hasMessageOutput = response.getBody().output().stream().anyMatch(item -> "message".equals(item.type()));

		assertThat(hasMessageOutput).as("Response should contain a message with the answer").isTrue();

		// Verify usage information includes the web search
		assertThat(response.getBody().usage()).isNotNull();
		assertThat(response.getBody().usage().totalTokens()).isPositive();
	}

}
