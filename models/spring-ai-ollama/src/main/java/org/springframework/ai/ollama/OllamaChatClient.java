/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.ollama;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.api.OllamaApi.ChatRequest;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

/**
 * {@link ChatClient} implementation for {@literal Ollma}.
 *
 * Ollama allows developers to run large language models and generate embeddings locally.
 * It supports open-source models available on [Ollama AI
 * Library](https://ollama.ai/library). - Llama 2 (7B parameters, 3.8GB size) - Mistral
 * (7B parameters, 4.1GB size)
 *
 * Please refer to the <a href="https://ollama.ai/">official Ollama website</a> for the
 * most up-to-date information on available models.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class OllamaChatClient implements ChatClient, StreamingChatClient {

	private final OllamaApi chatApi;

	private String model = "orca-mini";

	private Map<String, Object> clientOptions;

	private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public OllamaChatClient(OllamaApi chatApi) {
		this.chatApi = chatApi;
	}

	public OllamaChatClient withModel(String model) {
		this.model = model;
		return this;
	}

	public OllamaChatClient withOptions(Map<String, Object> options) {
		this.clientOptions = options;
		return this;
	}

	public OllamaChatClient withOptions(OllamaOptions options) {
		this.clientOptions = options.toMap();
		return this;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		OllamaApi.ChatResponse response = this.chatApi.chat(request(prompt, this.model, false));
		var generator = new Generation(response.message().content());
		if (response.promptEvalCount() != null && response.evalCount() != null) {
			generator = generator
				.withGenerationMetadata(ChatGenerationMetadata.from("unknown", extractUsage(response)));
		}
		return new ChatResponse(List.of(generator));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		Flux<OllamaApi.ChatResponse> response = this.chatApi.streamingChat(request(prompt, this.model, true));

		return response.map(chunk -> {
			Generation generation = (chunk.message() != null) ? new Generation(chunk.message().content())
					: new Generation("");
			if (Boolean.TRUE.equals(chunk.done())) {
				generation = generation
					.withGenerationMetadata(ChatGenerationMetadata.from("unknown", extractUsage(chunk)));
			}
			return new ChatResponse(List.of(generation));
		});
	}

	private Usage extractUsage(OllamaApi.ChatResponse response) {
		return new Usage() {

			@Override
			public Long getPromptTokens() {
				return response.promptEvalCount().longValue();
			}

			@Override
			public Long getGenerationTokens() {
				return response.evalCount().longValue();
			}
		};
	}

	private OllamaApi.ChatRequest request(Prompt prompt, String model, boolean stream) {

		List<OllamaApi.Message> ollamaMessages = prompt.getInstructions()
			.stream()
			.filter(message -> message.getMessageType() == MessageType.USER
					|| message.getMessageType() == MessageType.ASSISTANT
					|| message.getMessageType() == MessageType.SYSTEM)
			.map(m -> OllamaApi.Message.builder(toRole(m)).withContent(m.getContent()).build())
			.toList();

		// runtime options
		Map<String, Object> clientOptionsToUse = merge(prompt.getOptions(), this.clientOptions, HashMap.class);

		return ChatRequest.builder(model)
			.withStream(stream)
			.withMessages(ollamaMessages)
			.withOptions(clientOptionsToUse)
			.build();
	}

	public static Map<String, Object> objectToMap(Object source) {
		try {
			String json = OBJECT_MAPPER.writeValueAsString(source);
			return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
			});
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> T mapToClass(Map<String, Object> source, Class<T> clazz) {
		try {
			String json = OBJECT_MAPPER.writeValueAsString(source);
			return OBJECT_MAPPER.readValue(json, clazz);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> T merge(Object source, Object target, Class<T> clazz) {
		if (source == null) {
			source = Map.of();
		}
		Map<String, Object> sourceMap = objectToMap(source);
		Map<String, Object> targetMap = objectToMap(target);

		targetMap.putAll(sourceMap.entrySet()
			.stream()
			.filter(e -> e.getValue() != null)
			.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())));

		return mapToClass(targetMap, clazz);
	}

	private OllamaApi.Message.Role toRole(Message message) {

		switch (message.getMessageType()) {
			case USER:
				return Role.USER;
			case ASSISTANT:
				return Role.ASSISTANT;
			case SYSTEM:
				return Role.SYSTEM;
			default:
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
		}
	}

}