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

import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.metadata.ChoiceMetadata;
import org.springframework.ai.metadata.Usage;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.api.OllamaApi.ChatRequest;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;

import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.MessageType;

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
	public ChatResponse generate(Prompt prompt) {

		OllamaApi.ChatResponse response = this.chatApi.chat(request(prompt, this.model, false));
		var generator = new Generation(response.message().content());
		if (response.promptEvalCount() != null && response.evalCount() != null) {
			generator = generator.withChoiceMetadata(ChoiceMetadata.from("unknown", extractUsage(response)));
		}
		return new ChatResponse(List.of(generator));
	}

	@Override
	public Flux<ChatResponse> generateStream(Prompt prompt) {

		Flux<OllamaApi.ChatResponse> response = this.chatApi.streamingChat(request(prompt, this.model, true));

		return response.map(chunk -> {
			Generation generation = (chunk.message() != null) ? new Generation(chunk.message().content())
					: new Generation("");
			if (Boolean.TRUE.equals(chunk.done())) {
				generation = generation.withChoiceMetadata(ChoiceMetadata.from("unknown", extractUsage(chunk)));
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

		List<OllamaApi.Message> ollamaMessages = prompt.getMessages()
			.stream()
			.filter(message -> message.getMessageType() == MessageType.USER
					|| message.getMessageType() == MessageType.ASSISTANT)
			.map(m -> OllamaApi.Message.builder(toRole(m)).withContent(m.getContent()).build())
			.toList();

		return ChatRequest.builder(model)
			.withStream(stream)
			.withMessages(ollamaMessages)
			.withOptions(this.clientOptions)
			.build();
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