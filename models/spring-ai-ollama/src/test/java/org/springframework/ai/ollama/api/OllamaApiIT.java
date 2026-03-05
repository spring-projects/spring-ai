/*
 * Copyright 2023-2025 the original author or authors.
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

import java.util.List;
import java.util.stream.Collectors;

import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.ollama.BaseOllamaIT;
import org.springframework.ai.ollama.api.OllamaApi.ChatRequest;
import org.springframework.ai.ollama.api.OllamaApi.ChatResponse;
import org.springframework.ai.ollama.api.OllamaApi.EmbeddingsRequest;
import org.springframework.ai.ollama.api.OllamaApi.EmbeddingsResponse;
import org.springframework.ai.ollama.api.OllamaApi.Message;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.springframework.ai.util.ResourceUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Sun Yuhan
 * @author Nicolas Krier
 */
class OllamaApiIT extends BaseOllamaIT {

	private static final String CHAT_MODEL = OllamaModel.QWEN_2_5_3B.getName();

	private static final String EMBEDDING_MODEL = OllamaModel.NOMIC_EMBED_TEXT.getName();

	private static final String THINKING_MODEL = OllamaModel.QWEN3_4B_THINKING.getName();

	@BeforeAll
	static void beforeAll() {
		initializeOllama(CHAT_MODEL, EMBEDDING_MODEL, THINKING_MODEL);
	}

	@Test
	void chat() {
		var request = ChatRequest.builder(CHAT_MODEL)
			.stream(false)
			.messages(List.of(
					Message.builder(Role.SYSTEM)
						.content("You are geography teacher. You are talking to a student.")
						.build(),
					Message.builder(Role.USER)
						.content("What is the capital of Bulgaria and what is the size? "
								+ "What it the national anthem?")
						.build()))
			.options(OllamaChatOptions.builder().temperature(0.9).build())
			.build();

		ChatResponse response = getOllamaApi().chat(request);

		System.out.println(response);

		assertThat(response).isNotNull();
		assertThat(response.model()).contains(CHAT_MODEL);
		assertThat(response.done()).isTrue();
		assertThat(response.message().role()).isEqualTo(Role.ASSISTANT);
		assertThat(response.message().content()).contains("Sofia");
	}

	// Example from https://ollama.com/blog/structured-outputs
	@Test
	void jsonStructuredOutput() {
		var jsonSchemaAsText = ResourceUtils.getText("classpath:country-json-schema.json");
		var jsonSchema = ModelOptionsUtils.jsonToMap(jsonSchemaAsText);
		var messages = List.of(Message.builder(Role.USER).content("Tell me about Canada.").build());
		var request = ChatRequest.builder(CHAT_MODEL).format(jsonSchema).messages(messages).build();

		var response = getOllamaApi().chat(request);

		assertThat(response).isNotNull();
		var message = response.message();
		assertThat(message).isNotNull();
		assertThat(message.role()).isEqualTo(Role.ASSISTANT);
		var messageContent = message.content();
		assertThat(messageContent).isNotNull();
		JsonAssertions.assertThatJson(messageContent)
			.isObject()
			.containsOnlyKeys("name", "capital", "languages")
			.containsEntry("name", "Canada")
			.containsEntry("capital", "Ottawa")
			.containsEntry("languages", List.of("English", "French"));
	}

	@Test
	void streamingChat() {
		var request = ChatRequest.builder(CHAT_MODEL)
			.stream(true)
			.messages(List.of(Message.builder(Role.USER)
				.content("What is the capital of Bulgaria and what is the size? " + "What it the national anthem?")
				.build()))
			.options(OllamaChatOptions.builder().temperature(0.9).build().toMap())
			.build();

		Flux<ChatResponse> response = getOllamaApi().streamingChat(request);

		List<ChatResponse> responses = response.collectList().block();
		System.out.println(responses);

		assertThat(responses).isNotNull();
		assertThat(responses.stream()
			.filter(r -> r.message() != null)
			.map(r -> r.message().content())
			.collect(Collectors.joining(System.lineSeparator()))).contains("Sofia");

		ChatResponse lastResponse = responses.get(responses.size() - 1);
		assertThat(lastResponse.message().content()).isEmpty();
		assertThat(lastResponse.done()).isTrue();
	}

	@Test
	void embedText() {
		EmbeddingsRequest request = new EmbeddingsRequest(EMBEDDING_MODEL, "I like to eat apples");

		EmbeddingsResponse response = getOllamaApi().embed(request);

		assertThat(response).isNotNull();
		assertThat(response.embeddings()).hasSize(1);
		assertThat(response.embeddings().get(0)).hasSize(768);
		assertThat(response.model()).isEqualTo(EMBEDDING_MODEL);
		// Token count varies by Ollama version and tokenizer implementation
		assertThat(response.promptEvalCount()).isGreaterThan(0).isLessThanOrEqualTo(10);
		assertThat(response.loadDuration()).isGreaterThan(1);
		assertThat(response.totalDuration()).isGreaterThan(1);
	}

	@Test
	void think() {
		var request = ChatRequest.builder(THINKING_MODEL)
			.stream(false)
			.messages(List.of(
					Message.builder(Role.SYSTEM)
						.content("You are geography teacher. You are talking to a student.")
						.build(),
					Message.builder(Role.USER)
						.content("What is the capital of Bulgaria and what is the size? "
								+ "What it the national anthem?")
						.build()))
			.options(OllamaChatOptions.builder().temperature(0.9).build())
			.enableThinking()
			.build();

		ChatResponse response = getOllamaApi().chat(request);

		System.out.println(response);

		assertThat(response).isNotNull();
		assertThat(response.model()).contains(THINKING_MODEL);
		assertThat(response.done()).isTrue();
		assertThat(response.message().role()).isEqualTo(Role.ASSISTANT);
		assertThat(response.message().content()).contains("Sofia");
		assertThat(response.message().thinking()).isNotEmpty();
	}

	@Test
	void chatWithThinking() {
		var request = ChatRequest.builder(THINKING_MODEL)
			.stream(true)
			.messages(List.of(Message.builder(Role.USER)
				.content("What is the capital of Bulgaria and what is the size? " + "What it the national anthem?")
				.build()))
			.options(OllamaChatOptions.builder().temperature(0.9).build())
			.enableThinking()
			.build();

		Flux<ChatResponse> response = getOllamaApi().streamingChat(request);

		List<ChatResponse> responses = response.collectList().block();
		System.out.println(responses);

		assertThat(responses).isNotNull();
		assertThat(responses.stream()
			.filter(r -> r.message() != null)
			.map(r -> r.message().thinking())
			.collect(Collectors.joining(System.lineSeparator()))).contains("Sofia");

		ChatResponse lastResponse = responses.get(responses.size() - 1);
		assertThat(lastResponse.message().content()).isEmpty();
		assertNull(lastResponse.message().thinking());
		assertThat(lastResponse.done()).isTrue();
	}

	@Test
	void streamChatWithThinking() {
		var request = ChatRequest.builder(THINKING_MODEL)
			.stream(true)
			.messages(List.of(Message.builder(Role.USER).content("What are the planets in the solar system?").build()))
			.options(OllamaChatOptions.builder().temperature(0.9).build())
			.enableThinking()
			.build();

		Flux<ChatResponse> response = getOllamaApi().streamingChat(request);

		List<ChatResponse> responses = response.collectList().block();
		System.out.println(responses);

		assertThat(responses).isNotNull();
		assertThat(responses.stream()
			.filter(r -> r.message() != null)
			.map(r -> r.message().thinking())
			.collect(Collectors.joining(System.lineSeparator()))).contains("solar");

		ChatResponse lastResponse = responses.get(responses.size() - 1);
		assertThat(lastResponse.message().content()).isEmpty();
		assertNull(lastResponse.message().thinking());
		assertThat(lastResponse.done()).isTrue();
	}

	@Test
	void streamChatWithoutThinking() {
		var request = ChatRequest.builder(THINKING_MODEL)
			.stream(true)
			.messages(List.of(Message.builder(Role.USER).content("What are the planets in the solar system?").build()))
			.options(OllamaChatOptions.builder().temperature(0.9).build())
			.disableThinking()
			.build();

		Flux<ChatResponse> response = getOllamaApi().streamingChat(request);

		List<ChatResponse> responses = response.collectList().block();
		System.out.println(responses);

		assertThat(responses).isNotNull();

		assertThat(responses.stream()
			.filter(r -> r.message() != null)
			.map(r -> r.message().content())
			.collect(Collectors.joining(System.lineSeparator()))).contains("Earth");

		assertThat(responses.stream().filter(r -> r.message() != null).allMatch(r -> r.message().thinking() == null))
			.isTrue();

		ChatResponse lastResponse = responses.get(responses.size() - 1);
		assertThat(lastResponse.message().content()).isEmpty();
		assertNull(lastResponse.message().thinking());
		assertThat(lastResponse.done()).isTrue();
	}

}
